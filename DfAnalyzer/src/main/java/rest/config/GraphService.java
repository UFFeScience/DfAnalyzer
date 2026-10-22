package rest.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import qp.dataflow.Dependency;
import qp.dataflow.Transformation;
import rest.server.v2.AttributeInfo;
import rest.server.v2.GraphEdge;
import rest.server.v2.GraphNode;
import rest.server.v2.GraphResponse;
import rest.server.v2.LineageResponse;

/**
 * Monta o grafo de proveniência nas duas visões e resolve linhagem sobre ele.
 *
 * @author joao
 */
@Service
public class GraphService {

    // ------------------------------------------------------------------ grafo

    public GraphResponse buildGraph(int dataflowId, String dataflowTag, int version,
            Dataflow dataflow, GraphResponse.View view) {
        return view == GraphResponse.View.TRANSFORMATION
                ? transformationView(dataflowId, dataflowTag, version, dataflow)
                : datasetView(dataflowId, dataflowTag, version, dataflow);
    }

    /** Transformação como nó (Activity) e dataset como aresta (Entity). */
    private GraphResponse transformationView(int dataflowId, String dataflowTag, int version,
            Dataflow dataflow) {

        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (Transformation transformation : dataflow.getTransformations()) {
            String id = transformationId(transformation.getTag());
            nodes.put(id, new GraphNode(id, transformation.getTag(),
                    GraphNode.Kind.TRANSFORMATION, null, null, null));
        }

        for (Dependency dependency : dataflow.getDependencies()) {
            Transformation producer = dependency.getPreviousTransformation();
            Transformation consumer = dependency.getNextTransformation();
            Dataset dataset = dependency.getDataset();

            if (producer != null && consumer != null) {
                edges.add(new GraphEdge(
                        edgeId(transformationId(producer.getTag()), transformationId(consumer.getTag()), dataset.getTag()),
                        transformationId(producer.getTag()),
                        transformationId(consumer.getTag()),
                        dataset.getTag()));
                continue;
            }

            // Borda do fluxo: entra como nó de dataset para não desaparecer.
            String datasetNodeId = datasetId(dataset.getTag());
            if (!nodes.containsKey(datasetNodeId)) {
                nodes.put(datasetNodeId, new GraphNode(datasetNodeId, dataset.getTag(),
                        GraphNode.Kind.DATASET,
                        producer == null ? GraphNode.Role.SOURCE : GraphNode.Role.SINK,
                        attributesOf(dataset), null));
            }
            if (producer == null && consumer != null) {
                edges.add(new GraphEdge(
                        edgeId(datasetNodeId, transformationId(consumer.getTag()), dataset.getTag()),
                        datasetNodeId, transformationId(consumer.getTag()), dataset.getTag()));
            } else if (producer != null) {
                edges.add(new GraphEdge(
                        edgeId(transformationId(producer.getTag()), datasetNodeId, dataset.getTag()),
                        transformationId(producer.getTag()), datasetNodeId, dataset.getTag()));
            }
        }

        return new GraphResponse(dataflowId, dataflowTag, version,
                GraphResponse.View.TRANSFORMATION, new ArrayList<>(nodes.values()), edges);
    }

    /** Dataset como nó e transformação como aresta. */
    private GraphResponse datasetView(int dataflowId, String dataflowTag, int version,
            Dataflow dataflow) {

        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        Set<String> seenEdges = new LinkedHashSet<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (Dataset dataset : dataflow.getDatasets()) {
            String id = datasetId(dataset.getTag());
            nodes.put(id, new GraphNode(id, dataset.getTag(), GraphNode.Kind.DATASET,
                    roleOf(dataflow, dataset), attributesOf(dataset), null));
        }

        for (Transformation transformation : dataflow.getTransformations()) {
            Collection<Dataset> inputs = dataflow.getTransformationPreviousDatasets(transformation);
            Collection<Dataset> outputs = dataflow.getTransformationNextDatasets(transformation);

            for (Dataset input : inputs) {
                for (Dataset output : outputs) {
                    String source = datasetId(input.getTag());
                    String target = datasetId(output.getTag());
                    String id = edgeId(source, target, transformation.getTag());
                    if (seenEdges.add(id)) {
                        edges.add(new GraphEdge(id, source, target, transformation.getTag()));
                    }
                }
            }
        }

        return new GraphResponse(dataflowId, dataflowTag, version,
                GraphResponse.View.DATASET, new ArrayList<>(nodes.values()), edges);
    }

    // --------------------------------------------------------------- linhagem

    /** Linhagem a montante e a jusante de um nó. */
    public LineageResponse traceLineage(GraphResponse graph, String focusNodeId) {
        Map<String, List<String>> forward = new LinkedHashMap<>();
        Map<String, List<String>> backward = new LinkedHashMap<>();
        for (GraphEdge edge : graph.getEdges()) {
            adjacency(forward, edge.getSource()).add(edge.getTarget());
            adjacency(backward, edge.getTarget()).add(edge.getSource());
        }

        Set<String> upstream = reachable(backward, focusNodeId);
        Set<String> downstream = reachable(forward, focusNodeId);

        // Só entram arestas entre nós do mesmo lado do foco.
        Set<String> lineageNodes = new LinkedHashSet<>(upstream);
        lineageNodes.add(focusNodeId);
        lineageNodes.addAll(downstream);

        List<String> lineageEdges = new ArrayList<>();
        for (GraphEdge edge : graph.getEdges()) {
            boolean up = (upstream.contains(edge.getSource()) || edge.getSource().equals(focusNodeId))
                    && (upstream.contains(edge.getTarget()) || edge.getTarget().equals(focusNodeId));
            boolean down = (downstream.contains(edge.getSource()) || edge.getSource().equals(focusNodeId))
                    && (downstream.contains(edge.getTarget()) || edge.getTarget().equals(focusNodeId));
            if (up || down) {
                lineageEdges.add(edge.getId());
            }
        }

        return new LineageResponse(focusNodeId, new ArrayList<>(upstream),
                new ArrayList<>(downstream), lineageEdges);
    }

    private static List<String> adjacency(Map<String, List<String>> map, String key) {
        List<String> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        return list;
    }

    /** BFS iterativa, para não estourar a pilha em dataflow cíclico. */
    private static Set<String> reachable(Map<String, List<String>> adjacency, String start) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            List<String> neighbours = adjacency.get(current);
            if (neighbours == null) {
                continue;
            }
            for (String neighbour : neighbours) {
                if (!neighbour.equals(start) && visited.add(neighbour)) {
                    queue.addLast(neighbour);
                }
            }
        }
        return visited;
    }

    // ---------------------------------------------------------------- helpers

    private static GraphNode.Role roleOf(Dataflow dataflow, Dataset dataset) {
        boolean produced = !dataflow.getDatasetPreviousTransformations(dataset).isEmpty();
        boolean consumed = !dataflow.getDatasetNextTransformations(dataset).isEmpty();
        if (!produced) {
            return GraphNode.Role.SOURCE;
        }
        return consumed ? GraphNode.Role.INTERMEDIATE : GraphNode.Role.SINK;
    }

    private static List<AttributeInfo> attributesOf(Dataset dataset) {
        List<AttributeInfo> attributes = new ArrayList<>();
        if (dataset.getAttributes() != null) {
            for (Attribute attribute : dataset.getAttributes()) {
                attributes.add(new AttributeInfo(attribute.getName(),
                        attribute.getAttributeType() == null
                                ? null : attribute.getAttributeType().name()));
            }
        }
        return attributes;
    }

    /** O prefixo evita colisão entre transformação e dataset de mesma tag. */
    private static String transformationId(String tag) {
        return "t:" + tag;
    }

    private static String datasetId(String tag) {
        return "d:" + tag;
    }

    private static String edgeId(String source, String target, String label) {
        return source + "->" + target + "#" + label;
    }
}

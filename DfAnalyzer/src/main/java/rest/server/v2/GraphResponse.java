package rest.server.v2;

import java.util.List;

/**
 * O grafo de proveniência prospectiva de uma versão do dataflow.
 *
 * @author joao
 */
public class GraphResponse {

    public enum View {
        /** Nós são transformações (PROV Activity); arestas são datasets. */
        TRANSFORMATION,
        /** Nós são datasets (PROV Entity); arestas são transformações. */
        DATASET
    }

    private final int dataflowId;
    private final String dataflowTag;
    private final int version;
    private final View view;
    private final List<GraphNode> nodes;
    private final List<GraphEdge> edges;

    public GraphResponse(int dataflowId, String dataflowTag, int version, View view,
            List<GraphNode> nodes, List<GraphEdge> edges) {
        this.dataflowId = dataflowId;
        this.dataflowTag = dataflowTag;
        this.version = version;
        this.view = view;
        this.nodes = nodes;
        this.edges = edges;
    }

    public int getDataflowId() { return dataflowId; }
    public String getDataflowTag() { return dataflowTag; }
    public int getVersion() { return version; }
    public View getView() { return view; }
    public List<GraphNode> getNodes() { return nodes; }
    public List<GraphEdge> getEdges() { return edges; }
}

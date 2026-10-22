package rest.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import pde.enumeration.AttributeType;
import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import qp.dataflow.Dependency;
import qp.dataflow.Transformation;
import rest.server.v2.GraphEdge;
import rest.server.v2.GraphNode;
import rest.server.v2.GraphResponse;
import rest.server.v2.LineageResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * As duas visões do grafo e a linhagem.
 *
 * @author joao
 */
public class GraphServiceTest {

    private GraphService service;
    private Dataflow dataflow;

    @Before
    public void setUp() {
        service = new GraphService();
        dataflow = new Dataflow(1, "pipeline");

        Transformation preparar = new Transformation("preparar");
        Transformation dividir = new Transformation("dividir");
        Transformation balancear = new Transformation("balancear");
        dataflow.addTransformations(Arrays.asList(preparar, dividir, balancear));

        Dataset iPrep = dataset("iprep", "arquivo", "linhas");
        Dataset oPrep = dataset("oprep", "texto", "classe");
        Dataset oDiv = dataset("odiv", "particao");
        Dataset oBal = dataset("obal", "balanceado");
        dataflow.addDatasets(Arrays.asList(iPrep, oPrep, oDiv, oBal));

        dataflow.addDependencies(Arrays.asList(
                new Dependency(null, iPrep, preparar),
                new Dependency(preparar, oPrep, dividir),
                new Dependency(preparar, oPrep, balancear),
                new Dependency(dividir, oDiv, null),
                new Dependency(balancear, oBal, null)));
    }

    private static Dataset dataset(String tag, String... attributeNames) {
        List<Attribute> attributes = new ArrayList<>();
        for (String name : attributeNames) {
            attributes.add(new Attribute(name, AttributeType.TEXT));
        }
        Dataset dataset = new Dataset();
        dataset.setTag(tag);
        dataset.setAttributes(attributes);
        return dataset;
    }

    private GraphResponse graph(GraphResponse.View view) {
        return service.buildGraph(1, "pipeline", 1, dataflow, view);
    }

    private static Set<String> ids(List<GraphNode> nodes) {
        Set<String> result = new LinkedHashSet<>();
        for (GraphNode node : nodes) {
            result.add(node.getId());
        }
        return result;
    }

    private static Set<String> edgePairs(List<GraphEdge> edges) {
        Set<String> result = new LinkedHashSet<>();
        for (GraphEdge edge : edges) {
            result.add(edge.getSource() + ">" + edge.getTarget() + "(" + edge.getLabel() + ")");
        }
        return result;
    }

    // --------------------------------------------------- visão transformação

    @Test
    public void transformationViewPutsActivitiesInTheNodes() {
        GraphResponse response = graph(GraphResponse.View.TRANSFORMATION);

        assertTrue(ids(response.getNodes()).containsAll(
                Arrays.asList("t:preparar", "t:dividir", "t:balancear")));

        // O dataset intermediário vira aresta.
        assertFalse("oprep é intermediário: deve ser aresta, não nó",
                ids(response.getNodes()).contains("d:oprep"));

        Set<String> edges = edgePairs(response.getEdges());
        assertTrue(edges.contains("t:preparar>t:dividir(oprep)"));
        assertTrue(edges.contains("t:preparar>t:balancear(oprep)"));
    }

    @Test
    public void transformationViewKeepsTheEdgesOfTheFlowVisible() {
        GraphResponse response = graph(GraphResponse.View.TRANSFORMATION);
        Set<String> nodeIds = ids(response.getNodes());

        // Entrada e saída do fluxo não têm duas pontas e viram nós.
        assertTrue("a entrada do fluxo precisa aparecer", nodeIds.contains("d:iprep"));
        assertTrue("as saídas do fluxo precisam aparecer", nodeIds.contains("d:odiv"));
        assertTrue(nodeIds.contains("d:obal"));

        Set<String> edges = edgePairs(response.getEdges());
        assertTrue(edges.contains("d:iprep>t:preparar(iprep)"));
        assertTrue(edges.contains("t:dividir>d:odiv(odiv)"));
    }

    // -------------------------------------------------------- visão dataset

    @Test
    public void datasetViewPutsEntitiesInTheNodesAndActivitiesInTheEdges() {
        GraphResponse response = graph(GraphResponse.View.DATASET);

        assertEquals(ids(response.getNodes()),
                new LinkedHashSet<>(Arrays.asList("d:iprep", "d:oprep", "d:odiv", "d:obal")));

        Set<String> edges = edgePairs(response.getEdges());
        assertTrue(edges.contains("d:iprep>d:oprep(preparar)"));
        assertTrue(edges.contains("d:oprep>d:odiv(dividir)"));
        assertTrue(edges.contains("d:oprep>d:obal(balancear)"));
    }

    @Test
    public void datasetViewClassifiesTheRoleOfEachDataset() {
        GraphResponse response = graph(GraphResponse.View.DATASET);
        for (GraphNode node : response.getNodes()) {
            switch (node.getTag()) {
                case "iprep": assertEquals(GraphNode.Role.SOURCE, node.getRole()); break;
                case "oprep": assertEquals(GraphNode.Role.INTERMEDIATE, node.getRole()); break;
                default: assertEquals(GraphNode.Role.SINK, node.getRole()); break;
            }
        }
    }

    @Test
    public void datasetNodesCarryTheirAttributes() {
        GraphResponse response = graph(GraphResponse.View.DATASET);
        for (GraphNode node : response.getNodes()) {
            if ("iprep".equals(node.getTag())) {
                assertEquals(2, node.getAttributes().size());
                assertEquals("arquivo", node.getAttributes().get(0).getName());
                assertEquals("TEXT", node.getAttributes().get(0).getType());
            }
        }
    }

    // ------------------------------------------------------------ linhagem

    @Test
    public void lineageSeparatesUpstreamFromDownstream() {
        GraphResponse response = graph(GraphResponse.View.DATASET);
        LineageResponse lineage = service.traceLineage(response, "d:oprep");

        assertEquals(new LinkedHashSet<>(Arrays.asList("d:iprep")),
                new LinkedHashSet<>(lineage.getUpstream()));
        assertEquals(new LinkedHashSet<>(Arrays.asList("d:odiv", "d:obal")),
                new LinkedHashSet<>(lineage.getDownstream()));
    }

    @Test
    public void siblingsAreNotPartOfEachOthersLineage() {
        GraphResponse response = graph(GraphResponse.View.DATASET);
        LineageResponse lineage = service.traceLineage(response, "d:odiv");

        assertTrue("odiv descende de iprep e oprep",
                lineage.getUpstream().containsAll(Arrays.asList("d:iprep", "d:oprep")));
        assertTrue("odiv não afeta nada", lineage.getDownstream().isEmpty());
        assertFalse("obal é irmão de odiv, não descendente",
                lineage.getDownstream().contains("d:obal"));
        assertFalse("obal também não é ancestral",
                lineage.getUpstream().contains("d:obal"));
    }

    @Test
    public void lineageEdgesDoNotLightUpPathsThatBypassTheFocus() {
        GraphResponse response = graph(GraphResponse.View.DATASET);
        LineageResponse lineage = service.traceLineage(response, "d:odiv");

        // oprep->obal liga um ancestral do foco a um irmão dele.
        assertFalse(lineage.getEdges().contains("d:oprep->d:obal#balancear"));
        assertTrue(lineage.getEdges().contains("d:oprep->d:odiv#dividir"));
    }

    @Test
    public void lineageWorksInTheTransformationViewToo() {
        GraphResponse response = graph(GraphResponse.View.TRANSFORMATION);
        LineageResponse lineage = service.traceLineage(response, "t:dividir");

        assertTrue(lineage.getUpstream().contains("t:preparar"));
        assertTrue(lineage.getDownstream().contains("d:odiv"));
        assertFalse("balancear é irmão de dividir",
                lineage.getDownstream().contains("t:balancear"));
    }

    @Test
    public void aNodeWithNoNeighboursHasEmptyLineageInsteadOfFailing() {
        Dataflow isolated = new Dataflow(1, "vazio");
        isolated.addTransformations(Arrays.asList(new Transformation("sozinha")));
        GraphResponse response =
                service.buildGraph(1, "vazio", 1, isolated, GraphResponse.View.TRANSFORMATION);

        LineageResponse lineage = service.traceLineage(response, "t:sozinha");
        assertTrue(lineage.getUpstream().isEmpty());
        assertTrue(lineage.getDownstream().isEmpty());
        assertTrue(lineage.getEdges().isEmpty());
    }
}

package rest.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import pde.enumeration.AttributeType;
import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import qp.dataflow.Dependency;
import qp.dataflow.Transformation;
import qp.query.QueryGuard;
import qp.query.QuerySpecification;
import rest.config.QueryService.Anchor;
import rest.server.v2.ApiException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Filtro por execução sem banco, sobre a topologia de GraphServiceTest.
 *
 * @author joao
 */
public class QueryServiceExecutionFilterTest {

    private Dataflow dataflow;
    private Transformation preparar;
    private Dataset iPrep;
    private Dataset oPrep;

    @Before
    public void setUp() {
        dataflow = new Dataflow(1, "pipeline");

        preparar = new Transformation("preparar");
        Transformation dividir = new Transformation("dividir");
        Transformation balancear = new Transformation("balancear");
        dataflow.addTransformations(Arrays.asList(preparar, dividir, balancear));

        iPrep = dataset("iprep", "arquivo");
        oPrep = dataset("oprep", "texto", "classe");
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

    // ------------------------------------------------------------- âncora

    @Test
    public void picksTheProducingTransformationWhenThereIsOne() {
        Anchor anchor = QueryService.resolveExecutionAnchor(
                dataflow, Collections.singletonList("oprep.texto"));

        assertEquals("oprep", anchor.datasetTag);
        assertEquals("preparar", anchor.transformationTag);
    }

    @Test
    public void fallsBackToTheConsumingTransformationForASourceDataset() {
        // iprep é entrada do fluxo, sem produtora.
        Anchor anchor = QueryService.resolveExecutionAnchor(
                dataflow, Collections.singletonList("iprep.arquivo"));

        assertEquals("iprep", anchor.datasetTag);
        assertEquals("preparar", anchor.transformationTag);
    }

    @Test
    public void usesOnlyTheFirstProjectionToChooseTheAnchor() {
        Anchor anchor = QueryService.resolveExecutionAnchor(
                dataflow, Arrays.asList("oprep.texto", "odiv.particao"));

        assertEquals("oprep", anchor.datasetTag);
    }

    @Test
    public void rejectsAnEmptyProjectionList() {
        assertBadRequest(() ->
                QueryService.resolveExecutionAnchor(dataflow, new ArrayList<>()));
    }

    @Test
    public void rejectsAnUnqualifiedProjection() {
        assertBadRequest(() ->
                QueryService.resolveExecutionAnchor(
                        dataflow, Collections.singletonList("semponto")));
    }

    @Test
    public void rejectsAnUnknownDataset() {
        assertBadRequest(() ->
                QueryService.resolveExecutionAnchor(
                        dataflow, Collections.singletonList("naoexiste.coluna")));
    }

    // --------------------------------------------------------- seleção

    @Test
    public void buildsAnInListFromTheResolvedTaskIds() {
        String selection = QueryService.buildExecutionSelection(
                "oprep", "preparar", Arrays.asList(12, 13, 14));

        assertEquals("oprep.preparar_task_id IN (12, 13, 14)", selection);
    }

    @Test
    public void forcesZeroRowsWhenTheExecutionHasNoTaskOfThatTransformation() {
        String selection = QueryService.buildExecutionSelection(
                "oprep", "preparar", Collections.emptyList());

        assertEquals("oprep.id = -1", selection);
    }


    @Test
    public void generatedSelectionPassesTheGuard() {
        String selection = QueryService.buildExecutionSelection(
                "oprep", "preparar", Arrays.asList(1, 2, 3));

        QuerySpecification spec = new QuerySpecification(
                "pipeline", 1, null,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("oprep.texto"),
                Collections.singletonList(selection));

        QueryGuard.enforce(spec, dataflow);

        assertTrue(spec.getSelections().iterator().next()
                .startsWith("oprep.preparar_task_id IN"));
    }

    @Test
    public void theForcedZeroRowSelectionAlsoPassesTheGuard() {
        String selection = QueryService.buildExecutionSelection(
                "oprep", "preparar", Collections.emptyList());

        QuerySpecification spec = new QuerySpecification(
                "pipeline", 1, null,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("oprep.texto"),
                Collections.singletonList(selection));

        QueryGuard.enforce(spec, dataflow);
    }

    // ----------------------------------------------------------- auxiliares

    private static void assertBadRequest(Runnable action) {
        try {
            action.run();
            fail("esperava ApiException");
        } catch (ApiException expected) {
            assertEquals(400, expected.getStatus().value());
        }
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
}

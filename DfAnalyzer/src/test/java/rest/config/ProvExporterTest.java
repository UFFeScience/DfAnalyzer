package rest.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import pde.enumeration.AttributeType;
import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import qp.dataflow.Dependency;
import qp.dataflow.Transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * O mapeamento para PROV-JSON.
 *
 * @author joao
 */
public class ProvExporterTest {

    private ProvExporter exporter;
    private Dataflow dataflow;

    @Before
    public void setUp() {
        exporter = new ProvExporter();
        dataflow = new Dataflow(1, "pipeline");

        Transformation preparar = new Transformation("preparar");
        Transformation dividir = new Transformation("dividir");
        dataflow.addTransformations(Arrays.asList(preparar, dividir));

        Dataset entrada = dataset("iprep", "arquivo");
        Dataset meio = dataset("oprep", "texto", "classe");
        Dataset saida = dataset("odiv", "particao");
        dataflow.addDatasets(Arrays.asList(entrada, meio, saida));

        dataflow.addDependencies(Arrays.asList(
                new Dependency(null, entrada, preparar),
                new Dependency(preparar, meio, dividir),
                new Dependency(dividir, saida, null)));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(String name) {
        Map<String, Object> document = exporter.toProvJson("pipeline", 1, dataflow);
        Object section = document.get(name);
        assertNotNull("seção '" + name + "' ausente no documento PROV", section);
        return (Map<String, Object>) section;
    }

    @Test
    public void transformationsBecomeActivitiesAndDatasetsBecomeEntities() {
        assertEquals(2, section("activity").size());
        assertEquals(3, section("entity").size());
        assertTrue(section("activity").containsKey("dfa:dt_preparar"));
        assertTrue(section("entity").containsKey("dfa:ds_oprep"));
    }

    @Test
    public void theIntermediateDatasetAppearsInBothRelations() {
        // oprep é saída de 'preparar' e entrada de 'dividir'.
        assertTrue(section("wasGeneratedBy").containsKey("dfa:gen_preparar_oprep"));
        assertTrue(section("used").containsKey("dfa:used_dividir_oprep"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void relationsPointToIdsThatExistInTheDocument() {
        Map<String, Object> document = exporter.toProvJson("pipeline", 1, dataflow);
        Map<String, Object> entities = (Map<String, Object>) document.get("entity");
        Map<String, Object> activities = (Map<String, Object>) document.get("activity");

        for (String section : new String[] {"used", "wasGeneratedBy"}) {
            Map<String, Object> relations = (Map<String, Object>) document.get(section);
            for (Object relation : relations.values()) {
                Map<String, Object> pair = (Map<String, Object>) relation;
                assertTrue(section + " aponta para entidade inexistente",
                        entities.containsKey(pair.get("prov:entity")));
                assertTrue(section + " aponta para atividade inexistente",
                        activities.containsKey(pair.get("prov:activity")));
            }
        }
    }

    @Test
    public void anInputWithNoProducerIsNotGeneratedByAnything() {
        // iprep não é produzido, só usado.
        assertTrue(section("used").containsKey("dfa:used_preparar_iprep"));
        for (String key : section("wasGeneratedBy").keySet()) {
            assertTrue("entrada do fluxo nao pode ter sido gerada",
                    !key.endsWith("_iprep"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void entitiesCarryTheirAttributes() {
        Map<String, Object> entity =
                (Map<String, Object>) section("entity").get("dfa:ds_oprep");
        List<Map<String, String>> attributes =
                (List<Map<String, String>>) entity.get("dfa:attributes");

        assertEquals(2, attributes.size());
        assertEquals("texto", attributes.get(0).get("name"));
        assertEquals("TEXT", attributes.get(0).get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void theNamespaceIdentifiesDataflowAndVersion() {
        Map<String, Object> document = exporter.toProvJson("pipeline", 7, dataflow);
        Map<String, String> prefix = (Map<String, String>) document.get("prefix");

        assertEquals("http://www.w3.org/ns/prov#", prefix.get("prov"));
        assertTrue("o prefixo precisa distinguir versoes do mesmo dataflow",
                prefix.get("dfa").contains("pipeline") && prefix.get("dfa").contains("v7"));
    }
}

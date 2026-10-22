package rest.server.v2;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Mantém static/api/openapi.yaml e os DTOs da v2 em acordo.
 *
 * @author joao
 */
public class OpenApiContractTest {

    private static final String SPEC = "/static/api/openapi.yaml";

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemas() {
        try (InputStream in = getClass().getResourceAsStream(SPEC)) {
            assertNotNull("openapi.yaml nao encontrado no classpath em " + SPEC, in);
            Map<String, Object> root = (Map<String, Object>) new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            assertNotNull("components ausente", components);
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            assertNotNull("components.schemas ausente", schemas);
            return schemas;
        } catch (Exception ex) {
            throw new AssertionError("nao foi possivel ler " + SPEC, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> declaredProperties(String schemaName) {
        Map<String, Object> schema = (Map<String, Object>) schemas().get(schemaName);
        assertNotNull("schema " + schemaName + " ausente no contrato", schema);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull("schema " + schemaName + " sem properties", properties);
        return new TreeSet<>(properties.keySet());
    }

    /** Propriedades que o Jackson emite para um DTO, pelos getters. */
    private static Set<String> serializedProperties(Class<?> type) {
        Set<String> names = new TreeSet<>();
        for (Method method : type.getMethods()) {
            if (method.getParameterTypes().length != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = method.getName();
            String property;
            if (name.startsWith("get") && name.length() > 3) {
                property = name.substring(3);
            } else if (name.startsWith("is") && name.length() > 2
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                property = name.substring(2);
            } else {
                continue;
            }
            names.add(Character.toLowerCase(property.charAt(0)) + property.substring(1));
        }
        return names;
    }

    private void assertContractMatches(String schemaName, Class<?> dto) {
        assertEquals("O contrato e o DTO " + dto.getSimpleName() + " divergiram."
                + " Atualize static/api/openapi.yaml (e rode 'npm run api:types' no studio).",
                declaredProperties(schemaName), serializedProperties(dto));
    }

    @Test
    public void dataflowSummaryMatchesTheContract() {
        assertContractMatches("DataflowSummary", DataflowSummary.class);
    }

    @Test
    public void overviewMatchesTheContract() {
        assertContractMatches("Overview", Overview.class);
    }

    @Test
    public void pageEnvelopeMatchesTheContract() {
        assertContractMatches("DataflowSummaryPage", PageResponse.class);
    }

    @Test
    public void queryDtosMatchTheContract() {
        assertContractMatches("QueryRequest", QueryRequest.class);
        assertContractMatches("QueryResult", QueryResult.class);
        assertContractMatches("QueryColumn", QueryColumn.class);
    }

    @Test
    public void annotationDtosMatchTheContract() {
        assertContractMatches("Annotation", Annotation.class);
        assertContractMatches("AnnotationRequest", AnnotationRequest.class);
    }

    @Test
    public void analyticsDtosMatchTheContract() {
        assertContractMatches("VersionSummary", VersionSummary.class);
        assertContractMatches("ExecutionSummary", ExecutionSummary.class);
        assertContractMatches("TimelineRow", TimelineRow.class);
        assertContractMatches("TaskAnomaly", TaskAnomaly.class);
        assertContractMatches("ExecutionTimeline", ExecutionTimeline.class);
        assertContractMatches("IngestionAlert", IngestionAlert.class);
        assertContractMatches("PendingTransaction", PendingTransaction.class);
        assertContractMatches("IngestionHealth", IngestionHealth.class);
        assertContractMatches("IngestionThroughput", IngestionThroughput.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void taskAnomalyEnumMatchesTheContract() {
        assertEnumMatches("TaskAnomaly", "status", TaskAnomaly.Status.values());
    }

    @Test
    public void graphDtosMatchTheContract() {
        assertContractMatches("GraphResponse", GraphResponse.class);
        assertContractMatches("GraphNode", GraphNode.class);
        assertContractMatches("GraphEdge", GraphEdge.class);
        assertContractMatches("AttributeInfo", AttributeInfo.class);
        assertContractMatches("LineageResponse", LineageResponse.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void graphEnumsMatchTheContract() {
        assertEnumMatches("GraphNode", "kind", GraphNode.Kind.values());
        assertEnumMatches("GraphNode", "role", GraphNode.Role.values());
        assertEnumMatches("GraphResponse", "view", GraphResponse.View.values());
    }

    /** Compara o `enum` declarado numa propriedade do contrato com o do código. */
    @SuppressWarnings("unchecked")
    private void assertEnumMatches(String schemaName, String property, Enum<?>[] values) {
        Map<String, Object> schema = (Map<String, Object>) schemas().get(schemaName);
        assertNotNull("schema " + schemaName + " ausente", schema);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> declared = (Map<String, Object>) properties.get(property);
        assertNotNull(schemaName + "." + property + " ausente no contrato", declared);
        List<String> fromContract = (List<String>) declared.get("enum");
        assertNotNull(schemaName + "." + property + " sem enum no contrato", fromContract);

        Set<String> fromCode = new LinkedHashSet<>();
        for (Enum<?> value : values) {
            fromCode.add(value.name());
        }
        assertEquals("Os valores de " + schemaName + "." + property + " divergiram do contrato.",
                fromCode, new LinkedHashSet<>(fromContract));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void statusEnumMatchesTheContract() {
        Map<String, Object> schema = (Map<String, Object>) schemas().get("DataflowStatus");
        assertNotNull("schema DataflowStatus ausente", schema);
        List<String> declared = (List<String>) schema.get("enum");
        assertNotNull("DataflowStatus sem enum", declared);

        Set<String> fromContract = new LinkedHashSet<>(declared);
        Set<String> fromCode = new LinkedHashSet<>();
        for (DataflowSummary.Status status : DataflowSummary.Status.values()) {
            fromCode.add(status.name());
        }
        assertEquals("Os valores de DataflowStatus divergiram do contrato.",
                fromCode, fromContract);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void everyDocumentedPathIsUnderTheV2PrefixOrIsTheQueryInterface() {
        try (InputStream in = getClass().getResourceAsStream(SPEC)) {
            Map<String, Object> root = (Map<String, Object>) new Yaml().load(in);
            Map<String, Object> paths = (Map<String, Object>) root.get("paths");
            assertNotNull("paths ausente", paths);
            for (String path : paths.keySet()) {
                assertTrue("Caminho fora do escopo do contrato v2: " + path,
                        path.startsWith("/api/v2/") || path.startsWith("/query_interface/"));
            }
            assertTrue("O contrato deve descrever ao menos as duas rotas do catalogo",
                    paths.keySet().containsAll(
                            Arrays.asList("/api/v2/dataflows", "/api/v2/overview")));
        } catch (Exception ex) {
            throw new AssertionError("nao foi possivel ler " + SPEC, ex);
        }
    }
}

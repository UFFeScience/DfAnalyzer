package qp.query;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;

import pde.enumeration.AttributeType;
import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tentativas de injeção de SQL na consulta têm de ser recusadas.
 *
 * @author joao
 */
public class QueryGuardTest {

    private Dataflow dataflow;

    @Before
    public void setUp() {
        dataflow = new Dataflow(1, "annie");

        Dataset input = new Dataset();
        input.setTag("ipreparardados");
        input.setAttributes(Arrays.asList(
                new Attribute("arquivo", AttributeType.FILE),
                new Attribute("linhas", AttributeType.NUMERIC)));

        Dataset output = new Dataset();
        output.setTag("opreparardados");
        output.setAttributes(Arrays.asList(
                new Attribute("classe", AttributeType.TEXT),
                new Attribute("acuracia", AttributeType.NUMERIC)));

        dataflow.addDatasets(Arrays.asList(input, output));
    }

    private QuerySpecification spec(String message) {
        return new QuerySpecification("annie", 1, message);
    }

    private QuerySpecification enforce(String message) {
        QuerySpecification specification = spec(message);
        QueryGuard.enforce(specification, dataflow);
        return specification;
    }

    private static String only(Collection<String> values) {
        assertEquals(1, values.size());
        return values.iterator().next();
    }

    private String selection(String message) {
        return only(enforce(message).getSelections());
    }

    // -------------------------------------------------------- casos válidos

    @Test
    public void acceptsAQualifiedProjection() {
        assertEquals("opreparardados.classe",
                only(enforce("projection(opreparardados.classe)").getProjections()));
    }

    @Test
    public void acceptsComparisonSelections() {
        assertEquals("opreparardados.acuracia >= 0.9",
                selection("selection(opreparardados.acuracia >= 0.9)"));
        assertEquals("opreparardados.classe = 'positivo'",
                selection("selection(opreparardados.classe = 'positivo')"));
        assertEquals("ipreparardados.linhas <> -3",
                selection("selection(ipreparardados.linhas <> -3)"));
    }

    @Test
    public void acceptsLikeInBetweenAndNull() {
        assertEquals("opreparardados.classe LIKE 'pos%'",
                selection("selection(opreparardados.classe LIKE 'pos%')"));
        assertEquals("opreparardados.classe NOT IN ('a', 'b')",
                selection("selection(opreparardados.classe NOT IN ('a','b'))"));
        assertEquals("opreparardados.acuracia BETWEEN 0.5 AND 0.9",
                selection("selection(opreparardados.acuracia BETWEEN 0.5 AND 0.9)"));
        assertEquals("opreparardados.classe IS NOT NULL",
                selection("selection(opreparardados.classe IS NOT NULL)"));
    }

    @Test
    public void acceptsColumnToColumnComparison() {
        assertEquals("opreparardados.acuracia > ipreparardados.linhas",
                selection("selection(opreparardados.acuracia > ipreparardados.linhas)"));
    }

    @Test
    public void reEmitsWithTheCatalogSpellingAndNormalizedSpacing() {
        assertEquals("opreparardados.classe = 'x'",
                selection("selection(  OPREPARARDADOS.CLASSE='x'  )"));
    }

    @Test
    public void escapesQuotesInsideLiteralsInsteadOfRejectingThem() {
        // A aspa não escapa do literal.
        assertEquals("opreparardados.classe = 'O''Brien'",
                selection("selection(opreparardados.classe = 'O''Brien')"));
        // A tautologia continua literal.
        assertEquals("opreparardados.classe = 'x'' OR ''1''=''1'",
                selection("selection(opreparardados.classe = 'x'' OR ''1''=''1')"));
    }

    @Test
    public void acceptsTheIdColumnEveryDatasetViewCarries() {
        assertEquals("opreparardados.id = 1", selection("selection(opreparardados.id = 1)"));
    }

    @Test
    public void canonicalizesDatasetTagsInSourceAndTarget() {
        QuerySpecification specification =
                enforce("source(IPREPARARDADOS)\ntarget(opreparardados)");
        assertEquals("ipreparardados", only(specification.getSources()));
        assertEquals("opreparardados", only(specification.getTargets()));
    }

    /** handleMessage lê até o último ) da linha e descarta o resto. */
    @Test
    public void textAfterTheClosingParenthesisIsDropped() {
        assertEquals("opreparardados.acuracia > 0",
                selection("selection(opreparardados.acuracia > 0) ; DROP TABLE task"));
    }


    @Test
    public void injectionPayloadsAreAllRejected() {
        String[] payloads = {
            // Encadeamento de comandos.
            "projection(opreparardados.classe); DELETE FROM dataflow)",
            "selection(opreparardados.acuracia > 0 -- )",
            "selection(opreparardados.acuracia > 0 /* x */)",
            // Subconsulta fora do catálogo.
            "selection(opreparardados.acuracia > (SELECT MAX(id) FROM task))",
            "projection((SELECT tag FROM dataflow))",
            // Tautologia por literal quebrado.
            "selection(opreparardados.classe = 'x' OR '1'='1')",
            "projection(opreparardados.classe UNION SELECT tag FROM dataflow)",
            // Desvio do destino do COPY INTO.
            "selection(opreparardados.acuracia > 0 into '/tmp/pwned')",
            // Literal não terminado.
            "selection(opreparardados.classe = 'x)",
            // Coluna, dataset e tabela desconhecidos.
            "projection(opreparardados.senha)",
            "selection(task.id > 0)",
            "source(task)",
            "target(dataflow)",
            "projection(classe)",
            // Identificador entre aspas.
            "projection(\"opreparardados\".\"classe\")",
            // Chamada de função na projeção.
            "projection(count(opreparardados.classe))",
            // Função e mapeamento desconhecidos.
            "drop(opreparardados)",
            "mapping(physical); DROP TABLE task)",
        };

        for (String payload : payloads) {
            try {
                enforce(payload);
                fail("payload de injecao aceito: " + payload);
            } catch (QueryValidationException expected) {
                assertTrue("mensagem vazia para: " + payload,
                        expected.getMessage() != null && !expected.getMessage().isEmpty());
            }
        }
    }

    @Test
    public void unknownDataflowIsAClientError() {
        try {
            QueryGuard.enforce(spec("projection(opreparardados.classe)"), null);
            fail("dataflow nulo aceito");
        } catch (QueryValidationException expected) {
            assertTrue(expected.getMessage().contains("Dataflow"));
        }
    }
}

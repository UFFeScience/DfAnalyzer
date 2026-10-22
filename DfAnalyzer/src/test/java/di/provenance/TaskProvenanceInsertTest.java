package di.provenance;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import di.enumeration.dataflow.AttributeType;
import di.enumeration.dbms.DBMS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Os valores de um set vão como parâmetros do PreparedStatement, nunca concatenados no SQL. */
public class TaskProvenanceInsertTest {

    @Test
    public void insertSqlHasOnlyPlaceholdersNoValues() {
        String sql = TaskProvenance.buildInsertSQL(
                "ds_dados_lookup", "generate_task_id",
                Arrays.asList("categorias", "regioes"), DBMS.MONETDB);

        assertEquals(
                "INSERT INTO \"ds_dados_lookup\"(generate_task_id,categorias,regioes) VALUES (?,?,?)",
                sql);
    }

    @Test
    public void insertSqlWithoutAttributesStillHasTheTaskColumn() {
        String sql = TaskProvenance.buildInsertSQL(
                "ds_x", "t_task_id", Collections.<String>emptyList(), DBMS.MONETDB);

        assertEquals("INSERT INTO \"ds_x\"(t_task_id) VALUES (?)", sql);
    }

    @Test
    public void memsqlTableNameIsNotQuoted() {
        String sql = TaskProvenance.buildInsertSQL(
                "ds_x", "t_task_id", Arrays.asList("a"), DBMS.MEMSQL);

        assertEquals("INSERT INTO ds_x(t_task_id,a) VALUES (?,?)", sql);
    }

    @Test
    public void textValuesWithQuotesAndSqlArePassedThroughUntouched() {
        String pythonListRepr = "['Eletrônicos', 'Vestuário']";
        String injection = "x'); DROP TABLE task; --";

        assertEquals(pythonListRepr, TaskProvenance.toJdbcValue(AttributeType.TEXT, pythonListRepr));
        assertEquals(injection, TaskProvenance.toJdbcValue(AttributeType.TEXT, injection));
        assertEquals(injection, TaskProvenance.toJdbcValue(AttributeType.FILE, injection));
        assertEquals(injection, TaskProvenance.toJdbcValue(AttributeType.LONG_TEXT, injection));
    }

    @Test
    public void numericValuesBecomeDoubles() {
        assertEquals(600.0, (Double) TaskProvenance.toJdbcValue(AttributeType.NUMERIC, "600"), 0.0);
        assertEquals(2258867.08, (Double) TaskProvenance.toJdbcValue(AttributeType.NUMERIC, "2258867.08"), 0.0);
        assertEquals(-1.5e-3, (Double) TaskProvenance.toJdbcValue(AttributeType.NUMERIC, " -1.5e-3 "), 0.0);
    }

    @Test
    public void nonNumericValueInNumericColumnBecomesNullNotAnInventedNumber() {
        assertNull(TaskProvenance.toJdbcValue(AttributeType.NUMERIC, "None"));
        assertNull(TaskProvenance.toJdbcValue(AttributeType.NUMERIC, ""));
        assertNull(TaskProvenance.toJdbcValue(AttributeType.NUMERIC, "1); DROP TABLE task; --"));
        assertNull(TaskProvenance.toJdbcValue(AttributeType.NUMERIC, "nan"));
        assertNull(TaskProvenance.toJdbcValue(AttributeType.NUMERIC, "inf"));
        assertNull(TaskProvenance.toJdbcValue(AttributeType.NUMERIC, null));
    }

    @Test
    public void textLongerThanItsColumnBecomesNullInsteadOfFailingTheWholeSet() {
        String fits = repeat('a', TaskProvenance.TEXT_MAX_LENGTH);
        String tooLong = repeat('a', TaskProvenance.TEXT_MAX_LENGTH + 1);
        String fileTooLong = repeat('a', TaskProvenance.FILE_MAX_LENGTH + 1);

        assertEquals(fits, TaskProvenance.toJdbcValue(AttributeType.TEXT, fits));
        assertNull(TaskProvenance.toJdbcValue(AttributeType.TEXT, tooLong));
        assertNull(TaskProvenance.toJdbcValue(AttributeType.FILE, fileTooLong));
        // LONG_TEXT é coluna TEXT sem limite.
        assertEquals(tooLong, TaskProvenance.toJdbcValue(AttributeType.LONG_TEXT, tooLong));
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    @Test
    public void injectionAttemptNeverReachesTheSqlText() {
        String sql = TaskProvenance.buildInsertSQL(
                "ds_relatorio", "save_task_id", Arrays.asList("conteudo"), DBMS.MONETDB);

        assertFalse(sql.contains("DROP"));
        assertFalse(sql.contains("'"));
    }

    /** A produtora é procurada na versão e na execução da consumidora. */
    @Test
    public void dependencyLookupIsScopedToTheConsumersVersionAndExecution() {
        String sql = TaskProvenance.DEPENDENCY_TASK_SQL;

        assertTrue(sql.contains("p.df_version = c.df_version"));
        assertTrue(sql.contains("p.df_exec = c.df_exec"));
        assertTrue(sql.contains("c.id = ?"));
        assertFalse("valores entram por parametro, nunca no texto", sql.contains("'"));
    }
}

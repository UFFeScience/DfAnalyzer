package di.provenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import di.enumeration.dataflow.AttributeType;
import di.enumeration.dataflow.SetType;
import di.enumeration.dbms.DBMS;
import di.enumeration.process.TransactionType;
import di.object.dataflow.Attribute;
import di.object.dataflow.Dataflow;
import di.object.dataflow.Set;
import di.object.dataflow.Transformation;
import di.object.process.Transaction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;

/** Conjunto com a tag de outro dataflow só é aceito se a tabela existente tiver todas as colunas dele. */
public class DataSetTableCheckTest {

    private static Transformation transformation(String tag) {
        Transformation dt = new Transformation();
        dt.tag = tag;
        return dt;
    }

    private static Set set(String tag, SetType type, Transformation dt, Transformation dependency, String... attributes) {
        Set s = new Set();
        s.tag = tag;
        s.type = type;
        s.transformation = dt;
        s.dependencyTransformation = dependency;
        for (String name : attributes) {
            Attribute a = new Attribute();
            a.name = name;
            a.type = AttributeType.NUMERIC;
            s.addAttribute(a);
        }
        return s;
    }

    /** Carregar_CSV produz Dados_Carregados e Filtrar o consome. */
    private static Transaction transaction() {
        Transaction t = new Transaction(TransactionType.DATAFLOW, null, null, DBMS.MONETDB);
        Dataflow df = new Dataflow();
        df.dataflowTag = "Pipeline";
        t.addObject(df);
        Transformation carregar = transformation("Carregar_CSV");
        Transformation filtrar = transformation("Filtrar");
        t.addObject(set("Dados_Carregados", SetType.OUTPUT, carregar, null, "Linhas"));
        t.addObject(set("Dados_Carregados", SetType.INPUT, filtrar, carregar, "Linhas"));
        return t;
    }

    private static DataSetTableCheck.TableColumns tables(final Map<String, Collection<String>> tables) {
        return new DataSetTableCheck.TableColumns() {
            @Override
            public Collection<String> of(String tableName) {
                Collection<String> columns = tables.get(tableName);
                return columns == null ? Collections.<String>emptyList() : columns;
            }
        };
    }

    @Test
    public void requiredColumnsJoinEveryUseOfTheSameSetTag() {
        Map<String, TreeSet<String>> required = DataSetTableCheck.requiredColumns(transaction());
        assertEquals(Collections.singleton("ds_dados_carregados"), required.keySet());
        assertEquals(new TreeSet<>(Arrays.asList("carregar_csv_task_id", "filtrar_task_id", "linhas")),
                required.get("ds_dados_carregados"));
        assertEquals("pipeline", DataSetTableCheck.dataflowTag(transaction()));
    }

    @Test
    public void tableThatDoesNotExistIsNotAConflict() {
        assertTrue(DataSetTableCheck.conflicts(transaction(), tables(new HashMap<String, Collection<String>>())).isEmpty());
    }

    @Test
    public void tableOfAnotherDataflowWithOtherColumnsIsAConflict() {
        Map<String, Collection<String>> existing = new HashMap<>();
        existing.put("ds_dados_carregados",
                Arrays.asList("id", "carregar_dados_task_id", "limpar_task_id", "qtd_registros"));
        Map<String, TreeSet<String>> conflicts = DataSetTableCheck.conflicts(transaction(), tables(existing));
        assertEquals(new TreeSet<>(Arrays.asList("carregar_csv_task_id", "filtrar_task_id", "linhas")),
                conflicts.get("ds_dados_carregados"));
    }

    @Test
    public void tableWithEveryColumnIsReusedAsBefore() {
        Map<String, Collection<String>> existing = new HashMap<>();
        existing.put("ds_dados_carregados",
                Arrays.asList("id", "Carregar_CSV_task_id", "filtrar_task_id", "linhas", "outra"));
        assertTrue(DataSetTableCheck.conflicts(transaction(), tables(existing)).isEmpty());
    }
}

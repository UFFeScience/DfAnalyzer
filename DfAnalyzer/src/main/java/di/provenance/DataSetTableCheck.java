package di.provenance;

import di.dataflow.object.DataflowObject;
import di.enumeration.dataflow.DataflowType;
import di.enumeration.dataflow.SetType;
import di.main.Utils;
import di.object.dataflow.Attribute;
import di.object.dataflow.Dataflow;
import di.object.dataflow.Set;
import di.object.process.Transaction;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/** Detecta conjuntos de um dataflow novo cuja tabela já existe com outras colunas. */
public final class DataSetTableCheck {

    /** Colunas de uma tabela, ou vazio se ela não existe. */
    public interface TableColumns {
        Collection<String> of(String tableName);
    }

    private DataSetTableCheck() {
    }

    public static String dataflowTag(Transaction t) {
        for (DataflowObject o : t.getObjects()) {
            if (o.getType() == DataflowType.DATAFLOW && ((Dataflow) o).dataflowTag != null) {
                return ((Dataflow) o).dataflowTag.toLowerCase();
            }
        }
        return null;
    }

    public static Map<String, TreeSet<String>> requiredColumns(Transaction t) {
        Map<String, TreeSet<String>> required = new LinkedHashMap<>();
        for (DataflowObject o : t.getObjects()) {
            if (o.getType() != DataflowType.SET) {
                continue;
            }
            Set s = (Set) o;
            String table = Utils.getDataSetTableName(s.tag).toLowerCase();
            TreeSet<String> columns = required.get(table);
            if (columns == null) {
                columns = new TreeSet<>();
                required.put(table, columns);
            }
            if (s.transformation != null) {
                columns.add(s.transformation.tag.toLowerCase() + "_task_id");
            }
            if (s.type != SetType.OUTPUT && s.dependencyTransformation != null) {
                columns.add(s.dependencyTransformation.tag.toLowerCase() + "_task_id");
            }
            for (Attribute a : s.getAttributes()) {
                columns.add(a.extractorTag == null
                        ? a.name.toLowerCase()
                        : Utils.getExtractorFileColumn(a.extractorTag).toLowerCase());
            }
        }
        return required;
    }

    /** Colunas que faltam em cada tabela já existente. */
    public static Map<String, TreeSet<String>> conflicts(Transaction t, TableColumns existing) {
        Map<String, TreeSet<String>> conflicts = new LinkedHashMap<>();
        for (Map.Entry<String, TreeSet<String>> entry : requiredColumns(t).entrySet()) {
            Collection<String> present = existing.of(entry.getKey());
            if (present == null || present.isEmpty()) {
                continue;
            }
            TreeSet<String> missing = new TreeSet<>(entry.getValue());
            for (String column : present) {
                missing.remove(column.toLowerCase());
            }
            if (!missing.isEmpty()) {
                conflicts.put(entry.getKey(), missing);
            }
        }
        return conflicts;
    }
}

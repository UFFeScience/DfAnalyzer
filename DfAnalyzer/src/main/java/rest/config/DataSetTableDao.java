package rest.config;

import di.main.Utils;
import di.object.process.Transaction;
import di.provenance.DataSetTableCheck;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.sql2o.Connection;
import org.sql2o.Sql2o;

/** Verifica se um dataflow novo cabe nas tabelas de dados já existentes. */
@Repository
public class DataSetTableDao {

    private static final String COLUMNS_SQL =
            "SELECT c.name FROM sys.tables t JOIN sys.columns c ON c.table_id = t.id"
            + " WHERE t.name = :table AND t.system = false";

    private static final String OWNERS_SQL =
            "SELECT DISTINCT df.tag FROM data_set ds, dataflow df"
            + " WHERE ds.df_id = df.id AND ds.tag = :tag ORDER BY df.tag";

    private static final String TABLE_PREFIX = Utils.getDataSetTableName("");

    @Autowired
    private Sql2o sql2o;

    /** Devolve null quando o dataflow pode ser gravado, ou o motivo da recusa. */
    public String conflict(Transaction t) {
        String tag = DataSetTableCheck.dataflowTag(t);
        if (tag == null) {
            return null;
        }
        try (final Connection con = sql2o.open()) {
            Integer known = con.createQuery("SELECT COUNT(*) FROM dataflow WHERE tag = :tag")
                    .addParameter("tag", tag).executeScalar(Integer.class);
            if (known != null && known > 0) {
                return null;
            }
            Map<String, TreeSet<String>> conflicts = DataSetTableCheck.conflicts(t, new DataSetTableCheck.TableColumns() {
                @Override
                public Collection<String> of(String tableName) {
                    return con.createQuery(COLUMNS_SQL).addParameter("table", tableName)
                            .executeScalarList(String.class);
                }
            });
            if (conflicts.isEmpty()) {
                return null;
            }
            StringBuilder message = new StringBuilder("dataflow '" + tag + "' recusado: ");
            String separator = "";
            for (Map.Entry<String, TreeSet<String>> conflict : conflicts.entrySet()) {
                List<String> owners = con.createQuery(OWNERS_SQL)
                        .addParameter("tag", conflict.getKey().substring(TABLE_PREFIX.length()))
                        .executeScalarList(String.class);
                message.append(separator).append("a tabela ").append(conflict.getKey())
                        .append(" ja existe, usada por ").append(owners)
                        .append(", sem as colunas ").append(conflict.getValue());
                separator = "; ";
            }
            message.append(". As tabelas de dados sao nomeadas so pela tag do conjunto;"
                    + " use outra tag de conjunto neste dataflow.");
            return message.toString();
        }
    }
}

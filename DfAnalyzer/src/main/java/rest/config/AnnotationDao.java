package rest.config;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.sql2o.Connection;
import org.sql2o.Sql2o;
import org.sql2o.data.Row;
import org.sql2o.data.Table;

import rest.server.v2.Annotation;

/**
 * Leitura e escrita das anotações.
 *
 * @author joao
 */
@Repository
public class AnnotationDao {

    /** `yyyy-MM-dd HH:mm:ss`, o mesmo formato de `performance.starttime`. */
    private static final String TIMESTAMP = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private Sql2o sql2o;

    public List<Annotation> listByDataflow(int dataflowId) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(
                    "SELECT id, df_id, target, body, author, created_at"
                    + " FROM annotation WHERE df_id = :df ORDER BY id DESC;")
                    .addParameter("df", dataflowId)
                    .executeAndFetchTable();

            List<Annotation> annotations = new ArrayList<>(table.rows().size());
            for (Row row : table.rows()) {
                annotations.add(new Annotation(
                        row.getInteger("id"),
                        row.getInteger("df_id"),
                        row.getString("target"),
                        row.getString("body"),
                        row.getString("author"),
                        row.getString("created_at")));
            }
            return annotations;
        }
    }

    public Annotation create(int dataflowId, String target, String body, String author) {
        String createdAt = new SimpleDateFormat(TIMESTAMP).format(new Date());
        try (Connection con = sql2o.open()) {
            // O driver do MonetDB não devolve chaves geradas de forma confiável.
            Table next = con.createQuery(
                    "SELECT NEXT VALUE FOR \"annotation_id_seq\" AS id;")
                    .executeAndFetchTable();
            int id = next.rows().get(0).getInteger("id");

            con.createQuery(
                    "INSERT INTO annotation(id, df_id, target, body, author, created_at)"
                    + " VALUES (:id, :df, :target, :body, :author, :createdAt);")
                    .addParameter("id", id)
                    .addParameter("df", dataflowId)
                    .addParameter("target", target)
                    .addParameter("body", body)
                    .addParameter("author", author)
                    .addParameter("createdAt", createdAt)
                    .executeUpdate();

            return new Annotation(id, dataflowId, target, body, author, createdAt);
        }
    }

    public boolean delete(int annotationId) {
        try (Connection con = sql2o.open()) {
            Table existing = con.createQuery(
                    "SELECT id FROM annotation WHERE id = :id;")
                    .addParameter("id", annotationId)
                    .executeAndFetchTable();
            if (existing.rows().isEmpty()) {
                return false;
            }
            con.createQuery("DELETE FROM annotation WHERE id = :id;")
                    .addParameter("id", annotationId)
                    .executeUpdate();
            return true;
        }
    }
}

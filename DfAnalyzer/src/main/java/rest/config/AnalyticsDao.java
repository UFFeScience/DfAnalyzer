package rest.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.sql2o.Connection;
import org.sql2o.Sql2o;
import org.sql2o.data.Row;
import org.sql2o.data.Table;

import rest.server.v2.ExecutionSummary;
import rest.server.v2.IngestionAlert;
import rest.server.v2.TaskAnomaly;
import rest.server.v2.TimelineRow;
import rest.server.v2.VersionSummary;

/**
 * Consultas de analytics sobre execução, task e performance; task.status só assume RUNNING, FINISHED e FINISHED_WITH_ERROR.
 *
 * @author joao
 */
@Repository
public class AnalyticsDao {

    @Autowired
    private Sql2o sql2o;

    public boolean versionBelongsToDataflow(int dataflowId, int version) {
        try (Connection con = sql2o.open()) {
            Table result = con.createQuery(
                    "SELECT 1 AS found FROM dataflow_version"
                    + " WHERE df_id = :df AND version = :version;")
                    .addParameter("df", dataflowId)
                    .addParameter("version", version)
                    .executeAndFetchTable();
            return !result.rows().isEmpty();
        }
    }

    public Integer dataflowOfExecution(int executionId) {
        try (Connection con = sql2o.open()) {
            Table result = con.createQuery(
                    "SELECT v.df_id AS dfid FROM dataflow_execution e, dataflow_version v"
                    + " WHERE e.id = :exec AND v.version = e.df_version;")
                    .addParameter("exec", executionId)
                    .executeAndFetchTable();
            return result.rows().isEmpty() ? null : result.rows().get(0).getInteger("dfid");
        }
    }

    private static final String VERSION_SUMMARY_SQL =
            "SELECT v.version AS version, v.origin AS origin, v.generator AS generator,"
            + " (SELECT COUNT(*) FROM dataflow_execution e WHERE e.df_version = v.version) AS executions,"
            + " (SELECT COUNT(*) FROM task t WHERE t.df_version = v.version) AS tasks,"
            + " (SELECT COUNT(*) FROM task t WHERE t.df_version = v.version"
            + "    AND t.status = 'FINISHED_WITH_ERROR') AS errortasks,"
            + " (SELECT MAX(p.endtime) FROM performance p, task t"
            + "    WHERE t.df_version = v.version AND p.task_id = t.id) AS lastrun"
            + " FROM dataflow_version v"
            + " WHERE v.df_id = :df"
            + " ORDER BY v.version DESC;";

    public List<VersionSummary> getVersions(int dataflowId) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(VERSION_SUMMARY_SQL)
                    .addParameter("df", dataflowId)
                    .executeAndFetchTable();

            List<VersionSummary> summaries = new ArrayList<>(table.rows().size());
            for (Row row : table.rows()) {
                summaries.add(new VersionSummary(
                        row.getInteger("version"),
                        intOf(row, "executions"),
                        intOf(row, "tasks"),
                        intOf(row, "errortasks"),
                        row.getString("lastrun"),
                        row.getString("origin"),
                        row.getString("generator")));
            }
            return summaries;
        }
    }

    private static final String EXECUTION_SUMMARY_SELECT =
            "SELECT e.id AS id, e.df_version AS version, e.tag AS tag,"
            + " (SELECT COUNT(*) FROM task t WHERE t.df_exec = e.id) AS tasks,"
            + " (SELECT COUNT(*) FROM task t WHERE t.df_exec = e.id"
            + "    AND t.status = 'FINISHED_WITH_ERROR') AS errortasks,"
            + " (SELECT COUNT(*) FROM task t WHERE t.df_exec = e.id"
            + "    AND t.status = 'RUNNING') AS runningtasks,"
            + " (SELECT MIN(p.starttime) FROM performance p, task t"
            + "    WHERE t.df_exec = e.id AND p.task_id = t.id) AS starttime,"
            + " (SELECT MAX(p.endtime) FROM performance p, task t"
            + "    WHERE t.df_exec = e.id AND p.task_id = t.id) AS endtime,"
            + " (SELECT CAST(MAX(p.endtime) AS TIMESTAMP) - CAST(MIN(p.starttime) AS TIMESTAMP)"
            + "    FROM performance p, task t WHERE t.df_exec = e.id AND p.task_id = t.id) AS durationseconds"
            + " FROM dataflow_execution e";

    private static final String EXECUTION_SUMMARY_BY_VERSION_SQL =
            EXECUTION_SUMMARY_SELECT + " WHERE e.df_version = :version ORDER BY e.id DESC;";

    private static final String EXECUTION_SUMMARY_BY_ID_SQL =
            EXECUTION_SUMMARY_SELECT + " WHERE e.id = :exec;";

    public List<ExecutionSummary> getExecutions(int version) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(EXECUTION_SUMMARY_BY_VERSION_SQL)
                    .addParameter("version", version)
                    .executeAndFetchTable();

            List<ExecutionSummary> summaries = new ArrayList<>(table.rows().size());
            for (Row row : table.rows()) {
                summaries.add(toExecutionSummary(row));
            }
            return summaries;
        }
    }

    public ExecutionSummary getExecution(int executionId) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(EXECUTION_SUMMARY_BY_ID_SQL)
                    .addParameter("exec", executionId)
                    .executeAndFetchTable();
            return table.rows().isEmpty() ? null : toExecutionSummary(table.rows().get(0));
        }
    }

    private static ExecutionSummary toExecutionSummary(Row row) {
        return new ExecutionSummary(
                row.getInteger("id"),
                row.getInteger("version"),
                row.getString("tag"),
                intOf(row, "tasks"),
                intOf(row, "errortasks"),
                intOf(row, "runningtasks"),
                row.getString("starttime"),
                row.getString("endtime"),
                secondsOf(row, "durationseconds"));
    }

    /** Uma barra por transformação, do menor início ao maior fim entre as tasks dela. */
    private static final String TIMELINE_ROWS_SQL =
            "SELECT dt.tag AS transformation,"
            + " COUNT(DISTINCT t.id) AS taskcount,"
            + " SUM(CASE WHEN t.status = 'FINISHED_WITH_ERROR' THEN 1 ELSE 0 END) AS errortasks,"
            + " MIN(CAST(p.starttime AS TIMESTAMP)) AS mintstart,"
            + " MAX(CAST(p.endtime AS TIMESTAMP)) AS maxtend"
            + " FROM task t, data_transformation dt, performance p"
            + " WHERE t.df_exec = :exec AND dt.id = t.dt_id AND p.task_id = t.id"
            + " GROUP BY dt.tag"
            + " ORDER BY mintstart;";

    public List<TimelineRow> getTimelineRows(int executionId) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(TIMELINE_ROWS_SQL)
                    .addParameter("exec", executionId)
                    .executeAndFetchTable();

            List<Row> rows = table.rows();
            if (rows.isEmpty()) {
                return new ArrayList<>();
            }

            // Deslocamento a partir do início da primeira transformação.
            double globalStart = rows.stream()
                    .mapToDouble(r -> epochSeconds(r, "mintstart"))
                    .min().orElse(0);

            List<TimelineRow> result = new ArrayList<>(rows.size());
            for (Row row : rows) {
                double start = epochSeconds(row, "mintstart");
                double end = epochSeconds(row, "maxtend");
                result.add(new TimelineRow(
                        row.getString("transformation"),
                        intOf(row, "taskcount"),
                        intOf(row, "errortasks"),
                        round(start - globalStart),
                        round(end - start)));
            }
            return result;
        }
    }

    private static final String ANOMALIES_SQL =
            "SELECT t.id AS id, dt.tag AS transformation, t.status AS status, t.error_msg AS errormsg"
            + " FROM task t, data_transformation dt"
            + " WHERE t.df_exec = :exec AND dt.id = t.dt_id"
            + " AND (t.status = 'FINISHED_WITH_ERROR' OR t.status = 'RUNNING')"
            + " ORDER BY t.id;";

    public List<TaskAnomaly> getAnomalies(int executionId) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(ANOMALIES_SQL)
                    .addParameter("exec", executionId)
                    .executeAndFetchTable();

            List<TaskAnomaly> anomalies = new ArrayList<>(table.rows().size());
            for (Row row : table.rows()) {
                anomalies.add(new TaskAnomaly(
                        row.getInteger("id"),
                        row.getString("transformation"),
                        TaskAnomaly.Status.valueOf(row.getString("status")),
                        row.getString("errormsg")));
            }
            return anomalies;
        }
    }

    private static final String INGESTION_TOTALS_SQL =
            "SELECT COUNT(*) AS tasks,"
            + " SUM(CASE WHEN t.status = 'FINISHED_WITH_ERROR' THEN 1 ELSE 0 END) AS errortasks,"
            + " SUM(CASE WHEN t.status = 'RUNNING' THEN 1 ELSE 0 END) AS runningtasks"
            + " FROM task t, dataflow_version v"
            + " WHERE v.df_id = :df AND t.df_version = v.version;";

    public int[] getIngestionTotals(int dataflowId) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(INGESTION_TOTALS_SQL)
                    .addParameter("df", dataflowId)
                    .executeAndFetchTable();
            Row row = table.rows().get(0);
            return new int[] { intOf(row, "tasks"), intOf(row, "errortasks"), intOf(row, "runningtasks") };
        }
    }

    private static final String INGESTION_ALERTS_SQL =
            "SELECT e.id AS id, e.tag AS tag,"
            + " SUM(CASE WHEN t.status = 'FINISHED_WITH_ERROR' THEN 1 ELSE 0 END) AS errortasks,"
            + " SUM(CASE WHEN t.status = 'RUNNING' THEN 1 ELSE 0 END) AS runningtasks"
            + " FROM dataflow_execution e, dataflow_version v, task t"
            + " WHERE v.df_id = :df AND e.df_version = v.version AND t.df_exec = e.id"
            + " GROUP BY e.id, e.tag"
            + " HAVING SUM(CASE WHEN t.status = 'FINISHED_WITH_ERROR' THEN 1 ELSE 0 END) > 0"
            + "     OR SUM(CASE WHEN t.status = 'RUNNING' THEN 1 ELSE 0 END) > 0"
            + " ORDER BY e.id DESC"
            + " LIMIT 10;";

    public List<IngestionAlert> getIngestionAlerts(int dataflowId) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(INGESTION_ALERTS_SQL)
                    .addParameter("df", dataflowId)
                    .executeAndFetchTable();

            List<IngestionAlert> alerts = new ArrayList<>(table.rows().size());
            for (Row row : table.rows()) {
                alerts.add(new IngestionAlert(
                        row.getInteger("id"),
                        row.getString("tag"),
                        intOf(row, "errortasks"),
                        intOf(row, "runningtasks")));
            }
            return alerts;
        }
    }

    private static final String TASK_IDS_FOR_EXECUTION_SQL =
            "SELECT t.id AS id FROM task t, data_transformation dt"
            + " WHERE t.df_exec = :exec AND dt.id = t.dt_id AND LOWER(dt.tag) = LOWER(:tag);";

    /** Ids de task de uma transformação numa execução, para o filtro por execução da consulta. */
    public List<Integer> getTaskIdsForExecution(int executionId, String transformationTag) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(TASK_IDS_FOR_EXECUTION_SQL)
                    .addParameter("exec", executionId)
                    .addParameter("tag", transformationTag)
                    .executeAndFetchTable();

            List<Integer> ids = new ArrayList<>(table.rows().size());
            for (Row row : table.rows()) {
                ids.add(row.getInteger("id"));
            }
            return ids;
        }
    }

    private static final String RECENT_ENDTIMES_SQL =
            "SELECT p.endtime AS endtime"
            + " FROM performance p, task t, dataflow_version v"
            + " WHERE v.df_id = :df AND t.df_version = v.version AND p.task_id = t.id"
            + " AND p.endtime IS NOT NULL AND p.endtime >= :since"
            + " ORDER BY p.endtime;";

    /** Endtime de toda task concluída desde sinceTimestamp (`yyyy-MM-dd HH:mm:ss`). */
    public List<String> getRecentEndTimes(int dataflowId, String sinceTimestamp) {
        try (Connection con = sql2o.open()) {
            Table table = con.createQuery(RECENT_ENDTIMES_SQL)
                    .addParameter("df", dataflowId)
                    .addParameter("since", sinceTimestamp)
                    .executeAndFetchTable();

            List<String> times = new ArrayList<>(table.rows().size());
            for (Row row : table.rows()) {
                times.add(row.getString("endtime"));
            }
            return times;
        }
    }

    private static int intOf(Row row, String column) {
        Object value = row.getObject(column);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** MonetDB devolve a diferença de dois TIMESTAMP como decimal de segundos. */
    private static Double secondsOf(Row row, String column) {
        Object value = row.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    /** Segundos desde a época, a partir do TIMESTAMP que o driver devolve. */
    private static double epochSeconds(Row row, String column) {
        Object value = row.getObject(column);
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).getTime() / 1000.0;
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).getTime() / 1000.0;
        }
        throw new IllegalStateException("valor de timestamp inesperado em " + column + ": " + value);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

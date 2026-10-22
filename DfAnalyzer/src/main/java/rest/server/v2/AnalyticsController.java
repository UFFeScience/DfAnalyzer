package rest.server.v2;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import rest.config.AnalyticsDao;
import rest.config.DbConnection;

/**
 * Rotas de analytics; leem só execução, task e performance, sem carregar o catálogo.
 *
 * @author joao
 */
@RestController
@RequestMapping(value = "/api/v2", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class AnalyticsController {

    @Autowired
    private AnalyticsDao analytics;

    @Autowired
    private DbConnection db;

    @GetMapping("/dataflows/{id}/versions")
    public List<VersionSummary> versions(@PathVariable("id") int dataflowId) {
        requireDataflow(dataflowId);
        return analytics.getVersions(dataflowId);
    }

    @GetMapping("/dataflows/{id}/versions/{version}/executions")
    public List<ExecutionSummary> executions(
            @PathVariable("id") int dataflowId, @PathVariable("version") int version) {

        requireDataflow(dataflowId);
        if (!analytics.versionBelongsToDataflow(dataflowId, version)) {
            throw ApiException.notFound(
                    "versao " + version + " nao existe neste dataflow");
        }
        return analytics.getExecutions(version);
    }

    @GetMapping("/dataflows/{id}/executions/{execId}/timeline")
    public ExecutionTimeline timeline(
            @PathVariable("id") int dataflowId, @PathVariable("execId") int execId) {

        requireDataflow(dataflowId);
        ExecutionSummary execution = requireExecution(dataflowId, execId);
        return new ExecutionTimeline(
                execution, analytics.getTimelineRows(execId), analytics.getAnomalies(execId));
    }

    @GetMapping("/dataflows/{id}/ingestion-health")
    public IngestionHealth ingestionHealth(@PathVariable("id") int dataflowId) {
        requireDataflow(dataflowId);
        int[] totals = analytics.getIngestionTotals(dataflowId);
        return new IngestionHealth(
                totals[0], totals[1], totals[2], analytics.getIngestionAlerts(dataflowId));
    }

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int WINDOW_HOURS = 24;

    @GetMapping("/dataflows/{id}/ingestion-throughput")
    public IngestionThroughput throughput(@PathVariable("id") int dataflowId) {
        requireDataflow(dataflowId);

        LocalDateTime now = LocalDateTime.now();
        String since = now.minusHours(WINDOW_HOURS).format(TIMESTAMP_FORMAT);
        List<String> endTimes = analytics.getRecentEndTimes(dataflowId, since);

        List<Integer> hourly = new ArrayList<>(WINDOW_HOURS);
        for (int i = 0; i < WINDOW_HOURS; i++) {
            hourly.add(0);
        }

        int lastHourTasks = 0;
        int recentHalfTasks = 0;
        int olderHalfTasks = 0;
        for (String endTime : endTimes) {
            LocalDateTime at = LocalDateTime.parse(endTime, TIMESTAMP_FORMAT);
            long hoursAgo = Duration.between(at, now).toHours();
            if (hoursAgo < 0 || hoursAgo >= WINDOW_HOURS) {
                continue;
            }
            int bucket = (int) (WINDOW_HOURS - 1 - hoursAgo);
            hourly.set(bucket, hourly.get(bucket) + 1);

            if (hoursAgo < 1) {
                lastHourTasks++;
            }
            if (hoursAgo < WINDOW_HOURS / 2) {
                recentHalfTasks++;
            } else {
                olderHalfTasks++;
            }
        }

        double tasksPerMinute = lastHourTasks / 60.0;
        Double changePercent = olderHalfTasks == 0 ? null
                : ((recentHalfTasks - olderHalfTasks) * 100.0) / olderHalfTasks;

        return new IngestionThroughput(hourly, round(tasksPerMinute), changePercent);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void requireDataflow(int dataflowId) {
        if (db.getDataflowTag(dataflowId) == null) {
            throw ApiException.notFound("dataflow " + dataflowId + " nao existe");
        }
    }

    private ExecutionSummary requireExecution(int dataflowId, int execId) {
        Integer owner = analytics.dataflowOfExecution(execId);
        if (owner == null || owner != dataflowId) {
            throw ApiException.notFound(
                    "execucao " + execId + " nao existe neste dataflow");
        }
        return analytics.getExecution(execId);
    }
}

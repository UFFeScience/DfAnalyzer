package rest.server.v2;

import java.util.List;

/**
 * Linha do tempo de uma execução: uma barra por transformação e as tasks com problema.
 *
 * @author joao
 */
public class ExecutionTimeline {

    private final ExecutionSummary execution;
    private final List<TimelineRow> rows;
    private final List<TaskAnomaly> anomalies;

    public ExecutionTimeline(ExecutionSummary execution, List<TimelineRow> rows,
            List<TaskAnomaly> anomalies) {
        this.execution = execution;
        this.rows = rows;
        this.anomalies = anomalies;
    }

    public ExecutionSummary getExecution() { return execution; }
    public List<TimelineRow> getRows() { return rows; }
    public List<TaskAnomaly> getAnomalies() { return anomalies; }
}

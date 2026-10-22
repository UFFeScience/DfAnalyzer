package rest.server.v2;

/**
 * Uma linha de GET /api/v2/dataflows/{id}/versions/{version}/executions.
 *
 * @author joao
 */
public class ExecutionSummary {

    private final int id;
    private final int version;
    private final String tag;
    private final int tasks;
    private final int errorTasks;
    private final int runningTasks;
    private final String startTime;
    private final String endTime;
    private final Double durationSeconds;

    public ExecutionSummary(int id, int version, String tag, int tasks, int errorTasks,
            int runningTasks, String startTime, String endTime, Double durationSeconds) {
        this.id = id;
        this.version = version;
        this.tag = tag;
        this.tasks = tasks;
        this.errorTasks = errorTasks;
        this.runningTasks = runningTasks;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
    }

    public int getId() { return id; }
    public int getVersion() { return version; }
    public String getTag() { return tag; }
    public int getTasks() { return tasks; }
    public int getErrorTasks() { return errorTasks; }
    public int getRunningTasks() { return runningTasks; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public Double getDurationSeconds() { return durationSeconds; }
}

package rest.server.v2;

/**
 * Uma linha de GET /api/v2/dataflows.
 *
 * @author joao
 */
public class DataflowSummary {

    public enum Status {
        /** Nenhuma task ingerida. */
        EMPTY,
        /** Alguma task em RUNNING. */
        RUNNING,
        /** Nenhuma em RUNNING, mas alguma em READY. */
        READY,
        /** Todas as tasks em FINISHED. */
        FINISHED
    }

    private final int id;
    private final String tag;
    private final int transformations;
    private final int datasets;
    private final int versions;
    /** Execuções em todas as versões. */
    private final int executions;
    private final int tasks;
    private final int tasksRunning;
    private final int tasksReady;
    /** `yyyy-MM-dd HH:mm:ss`, como em performance.endtime; null se não houver. */
    private final String lastRun;
    /** Média de segundos por registro de performance; null se não houver. */
    private final Double avgDurationSeconds;
    private final Status status;
    /** manual, generated ou curated; null se não declarado. */
    private final String origin;
    /** Quem gerou o mapeamento; null se não declarado. */
    private final String generator;

    public DataflowSummary(int id, String tag, int transformations, int datasets, int versions,
            int executions, int tasks, int tasksRunning, int tasksReady, String lastRun,
            Double avgDurationSeconds, String origin, String generator) {
        this.id = id;
        this.tag = tag;
        this.transformations = transformations;
        this.datasets = datasets;
        this.versions = versions;
        this.executions = executions;
        this.tasks = tasks;
        this.tasksRunning = tasksRunning;
        this.tasksReady = tasksReady;
        this.lastRun = lastRun;
        this.avgDurationSeconds = avgDurationSeconds;
        this.status = deriveStatus(tasks, tasksRunning, tasksReady);
        this.origin = origin;
        this.generator = generator;
    }

    private static Status deriveStatus(int tasks, int running, int ready) {
        if (tasks == 0) {
            return Status.EMPTY;
        }
        if (running > 0) {
            return Status.RUNNING;
        }
        return ready > 0 ? Status.READY : Status.FINISHED;
    }

    public int getId() { return id; }
    public String getTag() { return tag; }
    public int getTransformations() { return transformations; }
    public int getDatasets() { return datasets; }
    public int getVersions() { return versions; }
    public int getExecutions() { return executions; }
    public int getTasks() { return tasks; }
    public int getTasksRunning() { return tasksRunning; }
    public int getTasksReady() { return tasksReady; }
    public String getLastRun() { return lastRun; }
    public Double getAvgDurationSeconds() { return avgDurationSeconds; }
    public Status getStatus() { return status; }
    public String getOrigin() { return origin; }
    public String getGenerator() { return generator; }
}

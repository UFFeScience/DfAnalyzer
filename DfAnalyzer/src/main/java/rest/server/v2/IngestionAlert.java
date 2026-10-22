package rest.server.v2;

/**
 * Execução com tasks em RUNNING ou com erro.
 *
 * @author joao
 */
public class IngestionAlert {

    private final int executionId;
    private final String executionTag;
    private final int errorTasks;
    private final int runningTasks;

    public IngestionAlert(int executionId, String executionTag, int errorTasks, int runningTasks) {
        this.executionId = executionId;
        this.executionTag = executionTag;
        this.errorTasks = errorTasks;
        this.runningTasks = runningTasks;
    }

    public int getExecutionId() { return executionId; }
    public String getExecutionTag() { return executionTag; }
    public int getErrorTasks() { return errorTasks; }
    public int getRunningTasks() { return runningTasks; }
}

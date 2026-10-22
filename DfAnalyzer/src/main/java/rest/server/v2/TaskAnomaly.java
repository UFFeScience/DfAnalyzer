package rest.server.v2;

/**
 * Task que não terminou em FINISHED.
 *
 * @author joao
 */
public class TaskAnomaly {

    public enum Status {
        /** Ainda em RUNNING: em andamento ou travada. */
        RUNNING,
        /** Em FINISHED_WITH_ERROR. */
        FINISHED_WITH_ERROR
    }

    private final int taskId;
    private final String transformationTag;
    private final Status status;
    private final String errorMessage;

    public TaskAnomaly(int taskId, String transformationTag, Status status, String errorMessage) {
        this.taskId = taskId;
        this.transformationTag = transformationTag;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public int getTaskId() { return taskId; }
    public String getTransformationTag() { return transformationTag; }
    public Status getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}

package rest.server.v2;

/** Transação ainda na fila de ingestão. */
public class PendingTransaction {

    private final String type;
    private final String dataflowTag;

    public PendingTransaction(String type, String dataflowTag) {
        this.type = type;
        this.dataflowTag = dataflowTag;
    }

    /** DATAFLOW ou TASK. */
    public String getType() { return type; }
    /** Null quando a transação não informa o dataflow. */
    public String getDataflowTag() { return dataflowTag; }
}

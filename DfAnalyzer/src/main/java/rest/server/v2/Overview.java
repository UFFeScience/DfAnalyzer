package rest.server.v2;

/**
 * Contadores gerais e estado da fila de ingestão.
 *
 * @author joao
 */
public class Overview {

    private final long dataflows;
    private final long versions;
    private final long executions;
    private final long executionsLast7Days;
    private final long transformations;
    private final long tasks;
    private final int queuePending;
    private final int transactionsProcessed;
    private final double queueTotalTimeMs;
    private final double transactionProcessingSeconds;
    private final int ingestionErrors;
    private final String lastIngestionError;

    public Overview(long dataflows, long versions, long executions, long executionsLast7Days, long transformations,
            long tasks, int queuePending, int transactionsProcessed, double queueTotalTimeMs,
            double transactionProcessingSeconds, int ingestionErrors, String lastIngestionError) {
        this.dataflows = dataflows;
        this.versions = versions;
        this.executions = executions;
        this.executionsLast7Days = executionsLast7Days;
        this.transformations = transformations;
        this.tasks = tasks;
        this.queuePending = queuePending;
        this.transactionsProcessed = transactionsProcessed;
        this.queueTotalTimeMs = queueTotalTimeMs;
        this.transactionProcessingSeconds = transactionProcessingSeconds;
        this.ingestionErrors = ingestionErrors;
        this.lastIngestionError = lastIngestionError;
    }

    public long getDataflows() { return dataflows; }
    public long getVersions() { return versions; }
    public long getExecutions() { return executions; }
    public long getExecutionsLast7Days() { return executionsLast7Days; }
    public long getTransformations() { return transformations; }
    public long getTasks() { return tasks; }
    public int getQueuePending() { return queuePending; }
    public int getTransactionsProcessed() { return transactionsProcessed; }
    public double getQueueTotalTimeMs() { return queueTotalTimeMs; }
    /** Soma do tempo de processamento de cada transação, em segundos. */
    public double getTransactionProcessingSeconds() { return transactionProcessingSeconds; }
    public int getIngestionErrors() { return ingestionErrors; }
    public String getLastIngestionError() { return lastIngestionError; }
}

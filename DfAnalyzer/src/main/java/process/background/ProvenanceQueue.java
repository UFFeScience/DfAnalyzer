package process.background;

import di.enumeration.process.TransactionType;
import java.text.DecimalFormat;
import java.util.ArrayList;
import di.object.process.Transaction;
import di.object.dataflow.Dataflow;
import di.provenance.DataflowLoader;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import rest.config.DbConnection;
import utils.Utils;

/**
 *
 * @author vitor
 */
public class ProvenanceQueue extends Thread {

    private ArrayList<Dataflow> dataflowsInMemory = new ArrayList<Dataflow>();
    private DbConnection db = null;
    private long startTime;
    private long endTime;
    private final int batchSize = 100;
    public double transactionProcessingTime;

    public double queueTotalTime;
    public int transactionsProcessed;

    /** Falhas de ingestão desde que o processo subiu. */
    public volatile int ingestionErrors;
    public volatile String lastIngestionError;

    public void removeDataflow(String dfTag) {
        for (Dataflow df : dataflowsInMemory) { //TODO: change to a Map with df_tag as keys (to insert and remove instances more efficiently)
            if (matches(df, dfTag)) {
                dataflowsInMemory.remove(df);
                break;
            }
        }
    }

    public Dataflow getDataflow(String dfTag) {
        for (Dataflow df : dataflowsInMemory) { //TODO: change to a Map with df_tag as keys (to insert and remove instances more efficiently)
            if (matches(df, dfTag)) {
                return df;
            }
        }
        return null;
    }

    /** Devolve o dataflow do cache ou o reconstrói do banco. */
    private Dataflow resolveDataflow(String dfTag) {
        Dataflow cached = getDataflow(dfTag);
        if (cached != null) {
            return cached;
        }
        Dataflow loaded = DataflowLoader.load(db.getConnection(), dfTag);
        if (loaded != null) {
            dataflowsInMemory.add(loaded);
        }
        return loaded;
    }

    private static boolean matches(Dataflow df, String dfTag) {
        return df != null && df.dataflowTag != null && df.dataflowTag.equals(dfTag);
    }

    public enum DBOperation {
        PENDENT_TRANSACTION, RUN_TRANSACTION
    }

    public ConcurrentLinkedQueue pendentTransactions = new ConcurrentLinkedQueue<>();

    public ProvenanceQueue(DbConnection db) {
        this.db = db;
        this.queueTotalTime = 0;
        this.transactionsProcessed = 0;
    }

    private static final long IDLE_SLEEP_MS = 50;

    @Override
    public void run() {
        while (true) {
            int processed = 0;
            try {
                processed = runQueries();
            } catch (Exception ex) {
                // Se a thread morrer, todas as transações seguintes se perdem.
                ingestionErrors++;
                lastIngestionError = ex.getClass().getSimpleName()
                        + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
                Logger.getLogger(ProvenanceQueue.class.getName())
                        .log(Level.SEVERE, "falha ao ingerir transacao", ex);
            }

            if (processed == 0) {
                try {
                    Thread.sleep(IDLE_SLEEP_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public synchronized ArrayList<Transaction> handleTransaction(DBOperation oper, Transaction t) {
        ArrayList<Transaction> result = new ArrayList<>();
        if (oper == DBOperation.PENDENT_TRANSACTION) {
            t.queueStartTime = System.currentTimeMillis();
            pendentTransactions.add(t);
        } else if (oper == DBOperation.RUN_TRANSACTION) {
            while (!pendentTransactions.isEmpty() && result.size() < batchSize) {
                result.add((Transaction) pendentTransactions.remove());
            }
        }
        return result;
    }

    @SuppressWarnings("CallToThreadDumpStack")
    private int runQueries() {
        if (db == null) {
            return 0;
        }
        ArrayList<Transaction> transactions = handleTransaction(DBOperation.RUN_TRANSACTION, null);
        for (Transaction t : transactions) {
            startTime = System.currentTimeMillis();
            if ((t.getType() == TransactionType.TASK) && (t.getPath() == null)) {
                Provenance.performTransaction(db.getConnection(), t,
                        resolveDataflow(t.getDataflowTag()));
            } else if (t.getType() == TransactionType.SHUTDOWN) {
                System.out.println("Shutting down...");
                try {
                    Utils.runCommand("touch finish.token", null);
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(ProvenanceQueue.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.exit(900);
            } else {
                t.execute(db.getConnection());
            }
            endTime = System.currentTimeMillis();
            DecimalFormat df = new DecimalFormat("######.##");
            transactionProcessingTime = Double.valueOf(df.format(transactionProcessingTime + ((endTime - startTime) / 1000.00)));

            System.out.println("Current amount elapsed time: " + transactionProcessingTime);

            transactionsProcessed = transactionsProcessed + 1;
            t.queueEndTime = startTime;
            t.execStartTime = startTime;
            t.execEndTime = endTime;
            queueTotalTime = queueTotalTime + (t.queueEndTime - t.queueStartTime);

            // Transação de TASK não carrega o objeto Dataflow.
            Dataflow dataflow = t.getDataflowFromObjects();
            if (dataflow != null && dataflow.dataflowTag != null
                    && getDataflow(dataflow.dataflowTag) == null) {
                dataflowsInMemory.add(dataflow);
            }
        }
        return transactions.size();
    }

}

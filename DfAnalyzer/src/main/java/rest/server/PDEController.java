package rest.server;

import di.enumeration.dbms.DBMS;
import di.enumeration.process.TransactionType;
import di.json.JSONReader;
import di.object.process.DaemonDI;
import di.object.process.Transaction;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import process.background.ProvenanceQueue;
import rest.config.DataSetTableDao;
import rest.config.PDEHandler;

/**
 *
 * @author vitor
 */
@RestController
@RequestMapping("/pde")
public class PDEController {

    @Autowired
    private PDEHandler pdeHandler;

    @Autowired
    private DaemonDI daemonDI;

    @Autowired
    private DataSetTableDao dataSetTables;

    /** Resposta 409 com o motivo, ou null se o dataflow pode ser enfileirado. */
    private ResponseEntity<String> refusal(Transaction dfTransaction) {
        String conflict = dataSetTables.conflict(dfTransaction);
        if (conflict == null) {
            return null;
        }
        JSONObject body = new JSONObject();
        body.put("error", "DATASET_TABLE_CONFLICT");
        body.put("message", conflict);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body.toJSONString());
    }

    @PostMapping(value = "/dataflow")
    public ResponseEntity<String> dataflow(@RequestBody String message) {
        daemonDI.transactionsGenerated++;
        
        long parsingStart;
        long parsingEnd;
        long generationStart;
        long generationEnd;
        long queueingStart;
        long queueingEnd;

        parsingStart = System.currentTimeMillis();
        JSONObject dataflow = pdeHandler.runDataflowFunctions(message);
        parsingEnd = System.currentTimeMillis();

        generationStart = System.currentTimeMillis();
        Transaction dfTransaction = JSONReader.generationDataflowTransaction(pdeHandler.getDataflowFileName(), pdeHandler.getPath(), dataflow, DBMS.MONETDB);
        generationEnd = System.currentTimeMillis();

        dfTransaction.parsingStart = parsingStart;
        dfTransaction.parsingEnd = parsingEnd;
        dfTransaction.generationStart = generationStart;
        dfTransaction.generationEnd = generationEnd;

        ResponseEntity<String> refusal = refusal(dfTransaction);
        if (refusal != null) {
            return refusal;
        }

        queueingStart = System.currentTimeMillis();
        daemonDI.queue.handleTransaction(ProvenanceQueue.DBOperation.PENDENT_TRANSACTION, dfTransaction);
        queueingEnd = System.currentTimeMillis();

        dfTransaction.queueingStart = queueingStart;
        dfTransaction.queueingEnd = queueingEnd;

        // performance metrics
        daemonDI.parsingTime = daemonDI.parsingTime + (parsingEnd - parsingStart);
        daemonDI.generationTime = daemonDI.generationTime + (generationEnd - generationStart);
        daemonDI.queueingTime = daemonDI.queueingTime + (queueingEnd - queueingStart);
        
        return ResponseEntity.ok(dataflow.toJSONString());
    }

    @PostMapping(value = "/task")
    public String task(@RequestBody String message) {
        daemonDI.transactionsGenerated++;
        
        long parsingStart;
        long parsingEnd;
        long taskGenStart;
        long taskGenEnd;
        long queueingStart;
        long queueingEnd;
        
        parsingStart = System.currentTimeMillis();
        JSONObject task = pdeHandler.runTaskFunctions(message);
        parsingEnd = System.currentTimeMillis();
        
        taskGenStart = System.currentTimeMillis();
        Transaction taskTransaction = JSONReader.generationTaskTransaction(pdeHandler.getDataflowFileName(), pdeHandler.getPath(), task, DBMS.MONETDB);
        taskGenEnd = System.currentTimeMillis();

        taskTransaction.parsingStart = parsingStart;
        taskTransaction.parsingEnd = parsingEnd;
        taskTransaction.generationStart = taskGenStart;
        taskTransaction.generationEnd = taskGenEnd;

        queueingStart = System.currentTimeMillis();        
        daemonDI.queue.handleTransaction(ProvenanceQueue.DBOperation.PENDENT_TRANSACTION, taskTransaction);
        queueingEnd = System.currentTimeMillis();

        taskTransaction.queueingStart = queueingStart;
        taskTransaction.queueingEnd = queueingEnd;

        // performance metrics
        daemonDI.parsingTime = daemonDI.parsingTime + (parsingEnd - parsingStart);
        daemonDI.generationTime = daemonDI.generationTime + (taskGenEnd - taskGenStart);
        daemonDI.queueingTime = daemonDI.queueingTime + (queueingEnd - queueingStart);

        return task.toJSONString();
    }

    @PostMapping(value = "/dataflow/json")
    public ResponseEntity<String> dataflow_ingest(@RequestBody String payload) {
        daemonDI.transactionsGenerated++;
        
        long parsingStart;
        long parsingEnd;
        long generationStart;
        long generationEnd;
        long queueingStart;
        long queueingEnd;

        parsingStart = System.currentTimeMillis();
        JSONObject dataflow_json = JSONReader.readDataflowFromRequest(payload);
        parsingEnd = System.currentTimeMillis();

        generationStart = System.currentTimeMillis();
        Transaction dfTransaction = JSONReader.generationDataflowTransaction(null, null, dataflow_json, DBMS.MONETDB);
        generationEnd = System.currentTimeMillis();

        dfTransaction.parsingStart = parsingStart;
        dfTransaction.parsingEnd = parsingEnd;
        dfTransaction.generationStart = generationStart;
        dfTransaction.generationEnd = generationEnd;

        ResponseEntity<String> refusal = refusal(dfTransaction);
        if (refusal != null) {
            return refusal;
        }

        queueingStart = System.currentTimeMillis();
        daemonDI.queue.handleTransaction(ProvenanceQueue.DBOperation.PENDENT_TRANSACTION, dfTransaction);
        queueingEnd = System.currentTimeMillis();

        dfTransaction.queueingStart = queueingStart;
        dfTransaction.queueingEnd = queueingEnd;

        // performance metrics
        daemonDI.parsingTime = daemonDI.parsingTime + (parsingEnd - parsingStart);
        daemonDI.generationTime = daemonDI.generationTime + (generationEnd - generationStart);
        daemonDI.queueingTime = daemonDI.queueingTime + (queueingEnd - queueingStart);
        
        return ResponseEntity.ok(dataflow_json.toJSONString());
    }

    @PostMapping(value = "/task/json")
    public String task_ingest(@RequestBody String payload) {
        daemonDI.transactionsGenerated++;
        
        long parsingStart;
        long parsingEnd;
        long generationStart;
        long generationEnd;
        long queueingStart;
        long queueingEnd;

        parsingStart = System.currentTimeMillis();
        JSONObject task_json = JSONReader.getTaskFromRequest(payload);
        parsingEnd = System.currentTimeMillis();

        generationStart = System.currentTimeMillis();
        Transaction tkTransaction = JSONReader.generationTaskTransaction(null, null, task_json, DBMS.MONETDB);
        generationEnd = System.currentTimeMillis();

        tkTransaction.parsingStart = parsingStart;
        tkTransaction.parsingEnd = parsingEnd;
        tkTransaction.generationStart = generationStart;
        tkTransaction.generationEnd = generationEnd;

        queueingStart = System.currentTimeMillis();
        daemonDI.queue.handleTransaction(ProvenanceQueue.DBOperation.PENDENT_TRANSACTION, tkTransaction);
        queueingEnd = System.currentTimeMillis();

        tkTransaction.queueingStart = queueingStart;
        tkTransaction.queueingEnd = queueingEnd;

        // performance metrics
        daemonDI.parsingTime = daemonDI.parsingTime + (parsingEnd - parsingStart);
        daemonDI.generationTime = daemonDI.generationTime + (generationEnd - generationStart);
        daemonDI.queueingTime = daemonDI.queueingTime + (queueingEnd - queueingStart);

        return task_json.toJSONString();
    }

    @RequestMapping("/shutdown")
    public void shutDown() {
        Transaction transaction = new Transaction(TransactionType.SHUTDOWN);
        daemonDI.queue.handleTransaction(ProvenanceQueue.DBOperation.PENDENT_TRANSACTION, transaction);
    }

}

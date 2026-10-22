package rest.server.v2;

import di.provenance.DataSetTableCheck;
import di.object.process.Transaction;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import process.background.ProvenanceQueue;
import qp.dataflow.Dataflow;
import rest.config.AnalyticsDao;
import rest.config.CatalogDao;
import rest.config.DbConnection;
import rest.config.GraphService;
import rest.config.ProvExporter;
import rest.config.QueryService;

/**
 * Endpoints de catálogo da API v2.
 *
 * @author joao
 */
@RestController
@RequestMapping(value = "/api/v2", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class CatalogV2Controller {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private CatalogDao catalog;

    @Autowired
    private ProvenanceQueue queue;

    @Autowired
    private DbConnection db;

    @Autowired
    private GraphService graphService;

    @Autowired
    private QueryService queryService;

    @Autowired
    private ProvExporter provExporter;

    @Autowired
    private AnalyticsDao analytics;

    @GetMapping("/dataflows")
    public PageResponse<DataflowSummary> dataflows(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = clamp(size);

        List<DataflowSummary> items =
                catalog.getDataflowSummaries(safeSize, safePage * safeSize);
        return new PageResponse<>(items, safePage, safeSize, catalog.countDataflows());
    }

    @GetMapping("/overview")
    public Overview overview() {
        return new Overview(
                catalog.countDataflows(),
                catalog.countVersions(),
                catalog.countExecutions(),
                catalog.countExecutionsSince(LocalDateTime.now().minusDays(7)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))),
                catalog.countTransformations(),
                catalog.countTasks(),
                queue.pendentTransactions.size(),
                queue.transactionsProcessed,
                queue.queueTotalTime,
                queue.transactionProcessingTime,
                queue.ingestionErrors,
                queue.lastIngestionError);
    }

    /** Até cem transações da fila de ingestão. */
    @GetMapping("/ingestion/pending")
    public List<PendingTransaction> pendingTransactions() {
        List<PendingTransaction> pending = new ArrayList<>();
        for (Object item : queue.pendentTransactions) {
            if (pending.size() >= 100) {
                break;
            }
            Transaction t = (Transaction) item;
            String tag = t.getDataflowTag() != null ? t.getDataflowTag() : DataSetTableCheck.dataflowTag(t);
            pending.add(new PendingTransaction(String.valueOf(t.getType()), tag));
        }
        return pending;
    }

    /** Grafo de proveniência prospectiva de uma versão do dataflow. */
    @GetMapping("/dataflows/{id}/graph")
    public GraphResponse graph(
            @PathVariable("id") int dataflowId,
            @RequestParam(value = "view", defaultValue = "transformation") String view,
            @RequestParam(value = "version", required = false) Integer version) {

        Resolved resolved = resolve(dataflowId, version);
        return graphService.buildGraph(dataflowId, resolved.tag, resolved.version,
                resolved.dataflow, parseView(view));
    }

    /** Linhagem de um nó: o que está a montante e a jusante dele. */
    @GetMapping("/dataflows/{id}/lineage")
    public LineageResponse lineage(
            @PathVariable("id") int dataflowId,
            @RequestParam("node") String nodeId,
            @RequestParam(value = "view", defaultValue = "transformation") String view,
            @RequestParam(value = "version", required = false) Integer version) {

        Resolved resolved = resolve(dataflowId, version);
        GraphResponse graph = graphService.buildGraph(dataflowId, resolved.tag,
                resolved.version, resolved.dataflow, parseView(view));

        boolean known = graph.getNodes().stream()
                .anyMatch(node -> node.getId().equals(nodeId));
        if (!known) {
            throw ApiException.notFound(
                    "no '" + nodeId + "' nao existe nesta visao do dataflow");
        }
        return graphService.traceLineage(graph, nodeId);
    }

    /** Executa uma consulta de proveniência e devolve os dados. */
    @PostMapping("/dataflows/{id}/query")
    public QueryResult query(@PathVariable("id") int dataflowId,
            @RequestBody QueryRequest request) {

        Resolved resolved = resolve(dataflowId, request.getVersion());
        if (request.getExecution() != null) {
            ExecutionSummary execution = analytics.getExecution(request.getExecution());
            if (execution == null || execution.getVersion() != resolved.version) {
                throw ApiException.badRequest(
                        "execucao " + request.getExecution()
                        + " nao pertence a versao " + resolved.version);
            }
        }
        return queryService.run(resolved.tag, resolved.version, resolved.dataflow, request);
    }

    /** Exporta a proveniência prospectiva em PROV-JSON. */
    @GetMapping("/dataflows/{id}/prov.json")
    public Map<String, Object> provJson(@PathVariable("id") int dataflowId,
            @RequestParam(value = "version", required = false) Integer version) {

        Resolved resolved = resolve(dataflowId, version);
        return provExporter.toProvJson(resolved.tag, resolved.version, resolved.dataflow);
    }

    private static GraphResponse.View parseView(String view) {
        try {
            return GraphResponse.View.valueOf(view.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(
                    "view invalida: '" + view + "'. Use 'transformation' ou 'dataset'.");
        }
    }

    private static final class Resolved {
        final String tag;
        final int version;
        final Dataflow dataflow;

        Resolved(String tag, int version, Dataflow dataflow) {
            this.tag = tag;
            this.version = version;
            this.dataflow = dataflow;
        }
    }

    private Resolved resolve(int dataflowId, Integer requestedVersion) {
        String tag = db.getDataflowTag(dataflowId);
        if (tag == null) {
            throw ApiException.notFound(
                    "dataflow " + dataflowId + " nao existe");
        }

        Integer version = requestedVersion != null
                ? requestedVersion : db.getLatestVersion(dataflowId);
        if (version == null) {
            throw ApiException.notFound(
                    "dataflow '" + tag + "' nao tem nenhuma versao salva");
        }

        Dataflow dataflow = db.loadDataflow(tag, version);
        if (dataflow == null) {
            throw ApiException.notFound(
                    "versao " + version + " do dataflow '" + tag + "' nao existe");
        }
        return new Resolved(tag, version, dataflow);
    }

    /** Limita o tamanho de página pedido pelo cliente. */
    private static int clamp(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}

package rest.config;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import qp.dataflow.Transformation;
import qp.query.AttrMapping;
import qp.query.MonetDbSqlQuery;
import qp.query.QueryGuard;
import qp.query.QueryProcessor;
import qp.query.QuerySpecification;
import rest.server.v2.QueryColumn;
import rest.server.v2.QueryRequest;
import rest.server.v2.QueryResult;

/**
 * Executa consultas de proveniência e devolve os dados.
 *
 * @author joao
 */
@Service
public class QueryService {

    /** Teto de linhas por página. */
    public static final int MAX_SIZE = 1000;
    public static final int DEFAULT_SIZE = 100;

    @Autowired
    private DbConnection db;

    @Autowired
    private AnalyticsDao analytics;

    public QueryResult run(String dataflowTag, int version, Dataflow dataflow,
            QueryRequest request) {

        int page = request.getPage() == null ? 0 : Math.max(request.getPage(), 0);
        int size = clampSize(request.getSize());

        List<String> selections = new ArrayList<>(
                request.getSelections() == null ? new ArrayList<>() : request.getSelections());
        if (request.getExecution() != null) {
            selections.add(executionFilterSelection(dataflow, request));
        }

        QuerySpecification spec = new QuerySpecification(
                dataflowTag, version, parseMapping(request.getMapping()),
                request.getSources(), request.getTargets(),
                request.getIncludes(), request.getExcludes(),
                request.getProjections(), selections);

        QueryGuard.enforce(spec, dataflow);

        if (spec.getProjections().isEmpty()) {
            throw ApiExceptionBridge.badRequest(
                    "Escolha ao menos um atributo para projetar.");
        }

        MonetDbSqlQuery query =
                new QueryProcessor(db, dataflow).runSqlQuery(spec, false);

        // Sem source/target o FROM sai vazio.
        if (query.getFromClause().toString().trim().isEmpty()) {
            java.util.Set<String> referenced = datasetsOf(spec.getProjections());
            if (referenced.size() > 1) {
                // Sem caminho entre os datasets o resultado seria um produto cartesiano.
                throw ApiExceptionBridge.badRequest(
                        "Os atributos escolhidos vem de mais de um dataset ("
                        + String.join(", ", referenced) + "). Informe de onde ate"
                        + " onde a consulta deve percorrer, para que as ligacoes"
                        + " entre eles sejam calculadas.");
            }
            for (String tag : referenced) {
                query.getFromClause().addElement(tag);
            }
        }

        // Uma linha a mais indica se há próxima página.
        String sql = withPaging(query.toString(), size + 1, page * size);

        long startedAt = System.nanoTime();
        List<List<String>> rows = fetch(sql, size + 1);
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }

        return new QueryResult(sql, describeColumns(spec, dataflow), rows,
                page, size, hasMore, round(elapsedMs));
    }

    /** Condição que restringe a consulta a uma execução. */
    private String executionFilterSelection(Dataflow dataflow, QueryRequest request) {
        Collection<String> projections = request.getProjections();
        Anchor anchor = resolveExecutionAnchor(dataflow, projections);
        List<Integer> taskIds = analytics.getTaskIdsForExecution(
                request.getExecution(), anchor.transformationTag);
        return buildExecutionSelection(anchor.datasetTag, anchor.transformationTag, taskIds);
    }

    static final class Anchor {
        final String datasetTag;
        final String transformationTag;

        Anchor(String datasetTag, String transformationTag) {
            this.datasetTag = datasetTag;
            this.transformationTag = transformationTag;
        }
    }

    /** Escolhe dataset e transformação só pelo catálogo, sem banco. */
    static Anchor resolveExecutionAnchor(Dataflow dataflow, Collection<String> projections) {
        if (projections == null || projections.isEmpty()) {
            throw ApiExceptionBridge.badRequest(
                    "Escolha ao menos um atributo para projetar antes de filtrar por execução.");
        }
        String firstProjection = projections.iterator().next();
        int dot = firstProjection == null ? -1 : firstProjection.indexOf('.');
        if (dot <= 0) {
            throw ApiExceptionBridge.badRequest(
                    "Projeção inválida: '" + firstProjection + "'.");
        }

        String datasetTag = firstProjection.substring(0, dot);
        Optional<Dataset> anchor = dataflow.getDataset(datasetTag);
        if (!anchor.isPresent()) {
            throw ApiExceptionBridge.badRequest("Dataset desconhecido: '" + datasetTag + "'.");
        }
        Dataset dataset = anchor.get();

        Collection<Transformation> producers = dataflow.getDatasetPreviousTransformations(dataset);
        Transformation transformation = !producers.isEmpty()
                ? producers.iterator().next()
                : firstOrNull(dataflow.getDatasetNextTransformations(dataset));
        if (transformation == null || transformation.getTag() == null) {
            throw ApiExceptionBridge.badRequest(
                    "'" + dataset.getTag() + "' não está ligado a nenhuma transformação;"
                    + " não é possível filtrar por execução.");
        }

        return new Anchor(dataset.getTag(), transformation.getTag());
    }

    /** Monta a condição a partir de ids de task já resolvidos no banco. */
    static String buildExecutionSelection(String datasetTag, String transformationTag,
            List<Integer> taskIds) {

        // Sem tasks na execução, a consulta tem de voltar vazia.
        if (taskIds.isEmpty()) {
            return datasetTag + ".id = -1";
        }

        String idList = taskIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return datasetTag + "." + transformationTag.toLowerCase(Locale.ROOT)
                + "_task_id IN (" + idList + ")";
    }

    private static Transformation firstOrNull(Collection<Transformation> transformations) {
        return transformations == null || transformations.isEmpty()
                ? null : transformations.iterator().next();
    }

    private static java.util.Set<String> datasetsOf(java.util.Collection<String> qualified) {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        for (String column : qualified) {
            int dot = column.indexOf('.');
            if (dot > 0) {
                tags.add(column.substring(0, dot));
            }
        }
        return tags;
    }

    private static int clampSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requested, MAX_SIZE);
    }

    private static AttrMapping.Type parseMapping(String mapping) {
        if (mapping == null || mapping.trim().isEmpty()) {
            return AttrMapping.Type.LOGICAL;
        }
        try {
            return AttrMapping.Type.valueOf(mapping.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiExceptionBridge.badRequest("Mapeamento desconhecido: '" + mapping
                    + "'. Use logical, physical ou hybrid.");
        }
    }

    private static String withPaging(String sql, int limit, int offset) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "\nLIMIT " + limit + " OFFSET " + offset + ";";
    }

    private List<List<String>> fetch(String sql, int maxRows) {
        List<List<String>> rows = new ArrayList<>();
        try (PreparedStatement st = db.getConnection().prepareStatement(sql)) {
            st.setMaxRows(maxRows);
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next()) {
                    List<String> row = new ArrayList<>(columns);
                    for (int i = 1; i <= columns; i++) {
                        Object value = rs.getObject(i);
                        row.add(value == null ? null : String.valueOf(value));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception ex) {
            // A SQL vem do catálogo; a falha é do servidor.
            throw ApiExceptionBridge.badRequest(
                    "Nao foi possivel executar a consulta: " + ex.getMessage());
        }
        return rows;
    }

    private static List<QueryColumn> describeColumns(QuerySpecification spec, Dataflow dataflow) {
        List<QueryColumn> columns = new ArrayList<>();
        for (String projection : spec.getProjections()) {
            columns.add(new QueryColumn(projection, typeOf(projection, dataflow)));
        }
        return columns;
    }

    private static String typeOf(String qualified, Dataflow dataflow) {
        int dot = qualified.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        Optional<Dataset> dataset = dataflow.getDataset(qualified.substring(0, dot));
        if (!dataset.isPresent()) {
            return null;
        }
        Optional<Attribute> attribute = dataset.get().getAttribute(qualified.substring(dot + 1));
        if (!attribute.isPresent() || attribute.get().getAttributeType() == null) {
            // Colunas fora do catálogo, como <transformacao>_task_id.
            return null;
        }
        return attribute.get().getAttributeType().name();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

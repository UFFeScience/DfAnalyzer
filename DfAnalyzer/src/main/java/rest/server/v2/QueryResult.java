package rest.server.v2;

import java.util.List;

/**
 * Resposta de POST /api/v2/dataflows/{id}/query.
 *
 * @author joao
 */
public class QueryResult {

    private final String sql;
    private final List<QueryColumn> columns;
    private final List<List<String>> rows;
    private final int page;
    private final int size;
    private final boolean hasMore;
    private final double elapsedMs;

    public QueryResult(String sql, List<QueryColumn> columns, List<List<String>> rows,
            int page, int size, boolean hasMore, double elapsedMs) {
        this.sql = sql;
        this.columns = columns;
        this.rows = rows;
        this.page = page;
        this.size = size;
        this.hasMore = hasMore;
        this.elapsedMs = elapsedMs;
    }

    public String getSql() { return sql; }
    public List<QueryColumn> getColumns() { return columns; }
    public List<List<String>> getRows() { return rows; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public boolean isHasMore() { return hasMore; }
    public double getElapsedMs() { return elapsedMs; }
}

package rest.server.v2;

import java.util.List;

/**
 * Envelope de paginação dos endpoints de coleção da v2.
 *
 * @author joao
 */
public class PageResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;

    public PageResponse(List<T> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotal() { return total; }
    public boolean isHasMore() { return (long) (page + 1) * size < total; }
}

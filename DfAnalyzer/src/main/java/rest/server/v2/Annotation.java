package rest.server.v2;

/**
 * Anotação ancorada num nó do grafo.
 *
 * @author joao
 */
public class Annotation {

    private final int id;
    private final int dataflowId;
    private final String target;
    private final String body;
    private final String author;
    private final String createdAt;

    public Annotation(int id, int dataflowId, String target, String body,
            String author, String createdAt) {
        this.id = id;
        this.dataflowId = dataflowId;
        this.target = target;
        this.body = body;
        this.author = author;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getDataflowId() { return dataflowId; }
    public String getTarget() { return target; }
    public String getBody() { return body; }
    public String getAuthor() { return author; }
    public String getCreatedAt() { return createdAt; }
}

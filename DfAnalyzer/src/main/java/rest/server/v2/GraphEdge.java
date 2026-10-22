package rest.server.v2;

/**
 * Uma aresta do grafo.
 *
 * @author joao
 */
public class GraphEdge {

    private final String id;
    private final String source;
    private final String target;
    private final String label;

    public GraphEdge(String id, String source, String target, String label) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.label = label;
    }

    public String getId() { return id; }
    public String getSource() { return source; }
    public String getTarget() { return target; }
    public String getLabel() { return label; }
}

package rest.server.v2;

import java.util.List;

/**
 * Resposta de linhagem: o que está a montante e a jusante de um nó.
 *
 * @author joao
 */
public class LineageResponse {

    private final String focus;
    private final List<String> upstream;
    private final List<String> downstream;
    private final List<String> edges;

    public LineageResponse(String focus, List<String> upstream, List<String> downstream,
            List<String> edges) {
        this.focus = focus;
        this.upstream = upstream;
        this.downstream = downstream;
        this.edges = edges;
    }

    public String getFocus() { return focus; }
    public List<String> getUpstream() { return upstream; }
    public List<String> getDownstream() { return downstream; }
    public List<String> getEdges() { return edges; }
}

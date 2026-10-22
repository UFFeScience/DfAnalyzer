package rest.server.v2;

import java.util.List;

/**
 * Um nó do grafo de proveniência, em qualquer das duas visões.
 *
 * @author joao
 */
public class GraphNode {

    public enum Kind {
        /** PROV Activity — uma transformação de dados. */
        TRANSFORMATION,
        /** PROV Entity — um conjunto de dados. */
        DATASET
    }

    /** Papel do dataset no fluxo. */
    public enum Role {
        /** Entra no fluxo sem ser produzido por nenhuma transformação. */
        SOURCE,
        /** Produzido e consumido — um resultado intermediário. */
        INTERMEDIATE,
        /** Produzido e não consumido por ninguém: uma saída do fluxo. */
        SINK
    }

    private final String id;
    private final String tag;
    private final Kind kind;
    private final Role role;
    private final List<AttributeInfo> attributes;
    private final Integer tasks;

    public GraphNode(String id, String tag, Kind kind, Role role,
            List<AttributeInfo> attributes, Integer tasks) {
        this.id = id;
        this.tag = tag;
        this.kind = kind;
        this.role = role;
        this.attributes = attributes;
        this.tasks = tasks;
    }

    public String getId() { return id; }
    public String getTag() { return tag; }
    public Kind getKind() { return kind; }
    public Role getRole() { return role; }
    public List<AttributeInfo> getAttributes() { return attributes; }
    public Integer getTasks() { return tasks; }
}

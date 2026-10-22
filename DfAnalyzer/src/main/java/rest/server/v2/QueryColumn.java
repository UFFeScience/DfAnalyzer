package rest.server.v2;

/**
 * Coluna do resultado, com o tipo declarado no catálogo.
 *
 * @author joao
 */
public class QueryColumn {

    private final String name;
    private final String type;

    public QueryColumn(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public String getType() { return type; }
}

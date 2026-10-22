package rest.server.v2;

/**
 * Um atributo de dataset: nome e tipo, como declarados na proveniência prospectiva.
 *
 * @author joao
 */
public class AttributeInfo {

    private final String name;
    private final String type;

    public AttributeInfo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public String getType() { return type; }
}

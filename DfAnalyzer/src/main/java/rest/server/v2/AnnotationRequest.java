package rest.server.v2;

/**
 * Corpo de criação de uma anotação.
 *
 * @author joao
 */
public class AnnotationRequest {

    private String target;
    private String body;
    private String author;

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}

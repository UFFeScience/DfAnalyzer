package rest.server.v2;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import rest.config.AnnotationDao;
import rest.config.DbConnection;

/**
 * Anotações sobre nós do grafo.
 *
 * @author joao
 */
@RestController
@RequestMapping(value = "/api/v2", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class AnnotationController {

    private static final int MAX_BODY = 4000;
    private static final int MAX_TARGET = 200;
    private static final int MAX_AUTHOR = 100;

    @Autowired
    private AnnotationDao annotations;

    @Autowired
    private DbConnection db;

    @GetMapping("/dataflows/{id}/annotations")
    public List<Annotation> list(@PathVariable("id") int dataflowId) {
        requireDataflow(dataflowId);
        return annotations.listByDataflow(dataflowId);
    }

    @PostMapping("/dataflows/{id}/annotations")
    public Annotation create(@PathVariable("id") int dataflowId,
            @RequestBody AnnotationRequest request) {

        requireDataflow(dataflowId);

        String target = trimmed(request.getTarget());
        String body = trimmed(request.getBody());

        if (target == null) {
            throw ApiException.badRequest("Informe o no a que a anotacao se refere (target).");
        }
        if (body == null) {
            throw ApiException.badRequest("A anotacao nao pode ser vazia.");
        }
        if (target.length() > MAX_TARGET) {
            throw ApiException.badRequest("Identificador de no longo demais.");
        }
        if (body.length() > MAX_BODY) {
            throw ApiException.badRequest(
                    "A anotacao passa de " + MAX_BODY + " caracteres.");
        }

        String author = trimmed(request.getAuthor());
        if (author != null && author.length() > MAX_AUTHOR) {
            author = author.substring(0, MAX_AUTHOR);
        }

        return annotations.create(dataflowId, target, body, author);
    }

    @DeleteMapping("/annotations/{annotationId}")
    public void delete(@PathVariable("annotationId") int annotationId) {
        if (!annotations.delete(annotationId)) {
            throw ApiException.notFound("Anotacao " + annotationId + " nao existe.");
        }
    }

    private void requireDataflow(int dataflowId) {
        if (db.getDataflowTag(dataflowId) == null) {
            throw ApiException.notFound("dataflow " + dataflowId + " nao existe");
        }
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

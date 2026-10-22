package rest.server.v2;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import qp.query.QueryValidationException;

/**
 * Traduz as exceções de cliente em respostas JSON com o status correto.
 *
 * @author joao
 */
@ControllerAdvice(basePackages = "rest.server.v2")
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handle(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getStatus().value(), ex.getMessage()));
    }

    /** O validador de consultas também é erro de cliente. */
    @ExceptionHandler(QueryValidationException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handle(QueryValidationException ex) {
        return ResponseEntity.badRequest().body(body(400, ex.getMessage()));
    }

    private static Map<String, Object> body(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);
        return body;
    }
}

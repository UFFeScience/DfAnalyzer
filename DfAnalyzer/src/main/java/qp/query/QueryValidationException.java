package qp.query;

/**
 * Consulta recusada pelo QueryGuard.
 *
 * @author joao
 */
public class QueryValidationException extends RuntimeException {

    public QueryValidationException(String message) {
        super(message);
    }
}

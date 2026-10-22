package rest.config;

import rest.server.v2.ApiException;

/**
 * Atalho para lançar ApiException de dentro de rest.config.
 *
 * @author joao
 */
final class ApiExceptionBridge {

    private ApiExceptionBridge() {
    }

    static RuntimeException badRequest(String message) {
        return ApiException.badRequest(message);
    }
}

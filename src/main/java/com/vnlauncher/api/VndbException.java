package com.vnlauncher.api;

/**
 * Error al hablar con VNDB: sin conexión, respuesta de error, JSON inesperado...
 *
 * <p>Es una excepción comprobada (extends Exception) a propósito: un fallo de red es algo
 * NORMAL en una app de escritorio (wifi caído, VNDB en mantenimiento), y el compilador
 * obliga a quien llama a decidir qué hacer en vez de dejar que reviente.
 */
public class VndbException extends Exception {

    /** Código HTTP de la respuesta, o 0 si ni siquiera hubo respuesta (fallo de conexión). */
    private final int statusCode;

    public VndbException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public VndbException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int statusCode() {
        return statusCode;
    }
}

package com.vnlauncher.testutil;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor HTTP local para tests que sirve unos bytes fijos como "imagen" en cualquier ruta.
 * Cuenta las peticiones para poder comprobar que algo NO se descarga dos veces.
 */
public class FakeImageServer implements AutoCloseable {

    public static final byte[] IMAGE_BYTES = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4}; // "JPEG" de mentira

    private final HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;

    public FakeImageServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = status == 200 ? IMAGE_BYTES : "not found".getBytes();
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    /** URL completa para una ruta, p. ej. url("/cv/95/75895.jpg"). */
    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public int requestCount() {
        return requests.get();
    }

    /** Cambiar el código HTTP que se responde (p. ej. 404 para simular un fallo). */
    public void respondWith(int status) {
        this.status = status;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

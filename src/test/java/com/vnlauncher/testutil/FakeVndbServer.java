package com.vnlauncher.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Servidor local que imita a la vez la API de VNDB (rutas /kana/...) y el servidor de imágenes
 * (cualquier otra ruta). Las respuestas de la API se preparan en una cola con {@link #enqueue}.
 */
public class FakeVndbServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<String> apiResponses = new ArrayDeque<>();
    // synchronizedList: el servidor atiende en otro hilo y el test lee desde el suyo.
    private final List<String> apiRequests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> imageRequests = Collections.synchronizedList(new ArrayList<>());

    public FakeVndbServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/kana/", exchange -> {
            apiRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body;
            synchronized (apiResponses) {
                body = apiResponses.poll();
            }
            if (body == null) {
                respond(exchange, 500, "sin respuesta preparada".getBytes(StandardCharsets.UTF_8));
            } else {
                respond(exchange, 200, body.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.createContext("/", exchange -> {
            imageRequests.add(exchange.getRequestURI().getPath());
            respond(exchange, 200, FakeImageServer.IMAGE_BYTES);
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** Base de la "API", para pasarla a VndbClient. */
    public URI apiBase() {
        return URI.create(baseUrl() + "/kana/");
    }

    /** http://127.0.0.1:PUERTO, para construir URLs de imagen falsas. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Añade una respuesta JSON (código 200) a la cola de la API. */
    public void enqueue(String json) {
        synchronized (apiResponses) {
            apiResponses.add(json);
        }
    }

    public List<String> apiRequests() {
        return List.copyOf(apiRequests);
    }

    public List<String> imageRequests() {
        return List.copyOf(imageRequests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

package com.vnlauncher.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vnlauncher.api.dto.CharacterDto;
import com.vnlauncher.api.dto.CharacterVnDto;
import com.vnlauncher.api.dto.VnDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del cliente SIN conectarse a VNDB: levantamos un servidor HTTP local (incluido en el JDK)
 * que devuelve las respuestas que le preparemos. Así los tests son rápidos, no dependen de
 * internet y no gastan peticiones del límite de VNDB.
 */
class VndbClientTest {

    /** Respuesta que dará el servidor falso: código HTTP + cuerpo. */
    private record FakeResponse(int status, String body) {
    }

    private HttpServer server;
    private final Deque<FakeResponse> responses = new ArrayDeque<>();
    private final List<String> requestBodies = new ArrayList<>();
    private final List<Duration> sleeps = new ArrayList<>();
    private VndbClient client;

    @BeforeEach
    void startServer() throws IOException {
        // Puerto 0 = que el sistema elija uno libre.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/kana/", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            FakeResponse response = responses.isEmpty()
                    ? new FakeResponse(500, "sin respuesta preparada") : responses.poll();
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/kana/");
        RateLimiter noLimit = new RateLimiter(1000, Duration.ofMinutes(5), InstantSource.system(), d -> { });
        client = new VndbClient(HttpClient.newHttpClient(), noLimit, sleeps::add, base);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void parsesRealSearchResponse() throws Exception {
        responses.add(new FakeResponse(200, fixture("vn-search-clannad.json")));

        List<VnDto> results = client.searchVn("clannad", 3);

        assertEquals(3, results.size());
        VnDto first = results.getFirst();
        assertEquals("v4", first.id());
        assertEquals("CLANNAD", first.title());
        assertEquals("2004-04-28", first.released());
        assertEquals("https://t.vndb.org/cv/95/75895.jpg", first.image().url());
        assertEquals(0.0, first.image().sexual());
        assertEquals("Key", first.developers().getFirst().name());
        // Los textos japoneses deben llegar bien (codificación UTF-8)
        assertEquals("CLANNAD -クラナド- 光見守る坂道で", results.get(1).alttitle());
    }

    @Test
    void sendsSearchQueryAsJson() throws Exception {
        responses.add(new FakeResponse(200, "{\"results\": [], \"more\": false}"));

        client.searchVn("fate", 10);

        JsonNode body = new ObjectMapper().readTree(requestBodies.getFirst());
        assertEquals("[\"search\",\"=\",\"fate\"]", body.get("filters").toString());
        assertEquals("searchrank", body.get("sort").asText());
        assertEquals(10, body.get("results").asInt());
        assertFalse(body.has("page"), "Los campos null no se envían");
    }

    @Test
    void parsesRealCharacterResponse() throws Exception {
        responses.add(new FakeResponse(200, fixture("character-v4.json")));

        List<CharacterDto> characters = client.primaryCharactersOfVn("v4");

        assertEquals(4, characters.size());
        CharacterDto nagisa = characters.stream().filter(c -> c.id().equals("c478")).findFirst().orElseThrow();
        assertEquals("Furukawa Nagisa", nagisa.name());
        assertEquals("古河 渚", nagisa.original());
        assertNotNull(nagisa.image().url());
        assertEquals(new CharacterVnDto("v4", "primary", 0), nagisa.vns().getFirst());
    }

    @Test
    void characterQueryUsesNestedFilterAndPaginates() throws Exception {
        responses.add(new FakeResponse(200, "{\"results\": [], \"more\": true}"));
        responses.add(new FakeResponse(200, "{\"results\": [], \"more\": false}"));

        client.primaryCharactersOfVn("v4");

        assertEquals(2, requestBodies.size(), "Pide la página 2 porque la 1 dijo more=true");
        ObjectMapper json = new ObjectMapper();
        JsonNode first = json.readTree(requestBodies.get(0));
        assertEquals("[\"and\",[\"vn\",\"=\",[\"id\",\"=\",\"v4\"]],[\"role\",\"=\",\"primary\"]]",
                first.get("filters").toString());
        assertEquals(1, first.get("page").asInt());
        assertEquals(2, json.readTree(requestBodies.get(1)).get("page").asInt());
    }

    @Test
    void retriesWithBackoffWhenThrottled() throws Exception {
        responses.add(new FakeResponse(429, "Throttled"));
        responses.add(new FakeResponse(429, "Throttled"));
        responses.add(new FakeResponse(200, "{\"results\": [], \"more\": false}"));

        assertTrue(client.searchVn("x", 1).isEmpty());

        assertEquals(3, requestBodies.size());
        assertEquals(List.of(Duration.ofSeconds(10), Duration.ofSeconds(20)), sleeps);
    }

    @Test
    void givesUpAfterMaxRetries() {
        for (int i = 0; i <= VndbClient.MAX_RETRIES; i++) {
            responses.add(new FakeResponse(429, "Throttled"));
        }

        VndbException e = assertThrows(VndbException.class, () -> client.searchVn("x", 1));

        assertEquals(429, e.statusCode());
        assertEquals(VndbClient.MAX_RETRIES + 1, requestBodies.size());
    }

    @Test
    void reportsServerErrorMessage() {
        responses.add(new FakeResponse(400, "Invalid 'fields' member: Field 'foo' not found"));

        VndbException e = assertThrows(VndbException.class, () -> client.searchVn("x", 1));

        assertEquals(400, e.statusCode());
        assertTrue(e.getMessage().contains("Field 'foo' not found"), e.getMessage());
        assertTrue(sleeps.isEmpty(), "Un 400 no se reintenta");
    }

    @Test
    void wrapsMalformedJson() {
        responses.add(new FakeResponse(200, "<html>esto no es JSON</html>"));

        assertThrows(VndbException.class, () -> client.searchVn("x", 1));
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            assertNotNull(in, "No existe el fichero de prueba " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

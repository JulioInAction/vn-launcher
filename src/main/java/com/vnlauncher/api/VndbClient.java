package com.vnlauncher.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vnlauncher.api.dto.CharacterDto;
import com.vnlauncher.api.dto.VnDto;
import com.vnlauncher.api.dto.VndbQuery;
import com.vnlauncher.api.dto.VndbResponse;
import com.vnlauncher.util.Sleeper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente de la API v2 (Kana) de VNDB. Solo sabe hablar HTTP + JSON: no toca la base de datos
 * ni la interfaz; devuelve DTOs y el servicio decide qué hacer con ellos.
 *
 * <p><b>Métodos síncronos (bloqueantes).</b> {@code searchVn} no vuelve hasta tener la respuesta.
 * Es más fácil de leer y testear que una cadena de CompletableFuture, y NUNCA se llama desde
 * el hilo de JavaFX: la interfaz lo ejecuta en segundo plano (ver SearchController).
 *
 * <p>Todas las peticiones pasan por el {@link RateLimiter}, y si aun así VNDB responde
 * 429 (Too Many Requests) se reintenta con esperas crecientes: 10 s, 20 s, 40 s.
 */
public class VndbClient {

    private static final System.Logger LOG = System.getLogger(VndbClient.class.getName());

    public static final URI DEFAULT_BASE_URI = URI.create("https://api.vndb.org/kana/");

    /** Campos que pedimos al buscar VNs. Pedir solo lo necesario hace las respuestas más ligeras. */
    static final String VN_FIELDS = "id, title, alttitle, released, description, "
            + "image.url, image.sexual, image.violence, "
            + "developers.id, developers.name, developers.original";

    static final String CHARACTER_FIELDS = "id, name, original, description, "
            + "image.url, image.sexual, image.violence, "
            + "vns.id, vns.role, vns.spoiler";

    /** Tope de páginas por consulta, por si acaso: 10 páginas = 1000 personajes. */
    static final int MAX_PAGES = 10;

    static final int MAX_RETRIES = 3;
    static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(10);

    private final HttpClient http;
    private final RateLimiter rateLimiter;
    private final Sleeper sleeper;
    private final URI baseUri;
    private final ObjectMapper mapper;

    /**
     * @param baseUri normalmente {@link #DEFAULT_BASE_URI}; los tests pasan la de un servidor falso local.
     *                Debe acabar en "/" para que resolve("vn") dé ".../kana/vn".
     */
    public VndbClient(HttpClient http, RateLimiter rateLimiter, Sleeper sleeper, URI baseUri) {
        this.http = http;
        this.rateLimiter = rateLimiter;
        this.sleeper = sleeper;
        this.baseUri = baseUri;
        this.mapper = JsonMapper.builder()
                // Si VNDB añade campos nuevos a la respuesta, ignorarlos en vez de fallar.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Busca VNs por texto (título, alias...), ordenadas por relevancia.
     *
     * @param maxResults como mucho 100 (límite de VNDB por página).
     */
    public List<VnDto> searchVn(String text, int maxResults) throws VndbException {
        VndbQuery query = new VndbQuery(
                List.of("search", "=", text), VN_FIELDS, "searchrank", maxResults, null);
        return post("vn", query, VnDto.class).results();
    }

    /**
     * Personajes de una VN que tienen rol "primary" en ALGUNA de sus VNs.
     *
     * <p>Cuidado: el filtro "role" de VNDB no se limita a esta VN. Un personaje secundario aquí
     * pero principal en la secuela también llega. El servicio vuelve a filtrar mirando el campo
     * vns de esta VN concreta. Aun así el filtro merece la pena: descarta a la mayoría de
     * secundarios y la respuesta es mucho más pequeña.
     *
     * <p>Pide páginas de 100 (el máximo) mientras VNDB diga que hay más ("more": true).
     */
    public List<CharacterDto> primaryCharactersOfVn(String vnId) throws VndbException {
        List<Object> filters = List.of("and",
                List.of("vn", "=", List.of("id", "=", vnId)),   // filtro anidado: "aparece en la VN con id..."
                List.of("role", "=", "primary"));
        List<CharacterDto> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            VndbQuery query = new VndbQuery(filters, CHARACTER_FIELDS, "name", 100, page);
            VndbResponse<CharacterDto> response = post("character", query, CharacterDto.class);
            all.addAll(response.results());
            if (!response.more()) {
                break;
            }
        }
        return all;
    }

    /**
     * Hace un POST a un endpoint de VNDB ("vn", "character"...) y convierte la respuesta.
     * Es genérico para reutilizarlo en las próximas fases con otros DTOs.
     */
    <T> VndbResponse<T> post(String endpoint, VndbQuery query, Class<T> itemType) throws VndbException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(endpoint))
                .header("Content-Type", "application/json")
                // Identificarse con un nombre de cliente genérico es de buena educación con APIs públicas.
                .header("User-Agent", "vn-launcher/0.1")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(toJson(query)))
                .build();

        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response = send(request);
            int status = response.statusCode();

            if (status == 200) {
                return parse(response.body(), itemType);
            }
            if (status == 429 && attempt < MAX_RETRIES) {
                // Espera exponencial: 10 s * 2^intento -> 10, 20, 40 s.
                // Multiplicar el Duration evita hacer cuentas a mano con milisegundos.
                Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << attempt);
                LOG.log(System.Logger.Level.WARNING,
                        "VNDB respondió 429 (demasiadas peticiones); reintento en {0} s", delay.toSeconds());
                sleepOrFail(delay);
                continue;
            }
            // Cualquier otro error: VNDB explica el motivo en texto plano en el cuerpo.
            throw new VndbException("VNDB respondió " + status + ": " + response.body().strip(), status);
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws VndbException {
        try {
            rateLimiter.acquire();
            LOG.log(System.Logger.Level.INFO, "Petición a VNDB: POST {0}", request.uri());
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // No usamos e.getMessage(): algunas (p. ej. ConnectException) no traen texto y saldría "null".
            // El detalle técnico se conserva como "causa" de la excepción para el registro.
            throw new VndbException("No se pudo conectar con VNDB. Comprueba tu conexión a internet.", e);
        } catch (InterruptedException e) {
            // Si alguien interrumpe el hilo (p. ej. al cerrar la app), restauramos la marca de
            // interrupción para que el código de más arriba también se entere.
            Thread.currentThread().interrupt();
            throw new VndbException("Petición a VNDB interrumpida", e);
        }
    }

    private void sleepOrFail(Duration delay) throws VndbException {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VndbException("Petición a VNDB interrumpida", e);
        }
    }

    private String toJson(VndbQuery query) {
        try {
            return mapper.writeValueAsString(query);
        } catch (JsonProcessingException e) {
            // Serializar un record nuestro no debería fallar nunca: si pasa, es un bug.
            throw new IllegalStateException(e);
        }
    }

    private <T> VndbResponse<T> parse(String json, Class<T> itemType) throws VndbException {
        // Por el "borrado de tipos" de Java, VndbResponse<VnDto>.class no existe.
        // Construimos el tipo genérico completo para que Jackson sepa qué hay en "results".
        JavaType type = mapper.getTypeFactory().constructParametricType(VndbResponse.class, itemType);
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new VndbException("Respuesta de VNDB con formato inesperado", e);
        }
    }
}

package com.vnlauncher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Avisa de que hay una versión nueva: mira la última release de GitHub y la compara con la que
 * está instalada. No descarga nada; «Ver novedades» solo abre la página de la release.
 *
 * <p>Si no se puede comprobar (sin red, GitHub caído, límite de peticiones) no es un error: queda
 * como {@link Status#FAILED} y nadie se entera salvo la línea de Ajustes. Un 404 es que aún no hay
 * ninguna release: se cuenta como «estás al día».
 */
public class UpdateService {

    private static final System.Logger LOG = System.getLogger(UpdateService.class.getName());

    public static final URI LATEST_RELEASE =
            URI.create("https://api.github.com/repos/JulioInAction/vn-launcher/releases/latest");
    private static final String RELEASES_PAGE = "https://github.com/JulioInAction/vn-launcher/releases/tag/";
    private static final String TRUSTED_PREFIX = "https://github.com/JulioInAction/vn-launcher/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** «v1.0.10», «1.0», «2.1.0-beta»: lo que cuenta son los números del principio. */
    private static final Pattern VERSION = Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)");

    public enum Status { AVAILABLE, UP_TO_DATE, FAILED }

    /** @param version sin la «v»: «1.0.1»; url: la página de la release */
    public record Release(String version, String url) {
    }

    /** @param release la versión nueva si {@code status} es AVAILABLE; si no, null */
    public record Result(Status status, Release release, Instant checkedAt) {
    }

    private final HttpClient http;
    private final URI endpoint;
    private final String currentVersion;
    private final SettingsService settings;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private volatile Result last;

    public UpdateService(HttpClient http, URI endpoint, String currentVersion, SettingsService settings,
                         Clock clock) {
        this.http = http;
        this.endpoint = endpoint;
        this.currentVersion = currentVersion;
        this.settings = settings;
        this.clock = clock;
    }

    /** La última comprobación de esta sesión, si hubo alguna. */
    public Optional<Result> lastResult() {
        return Optional.ofNullable(last);
    }

    /**
     * Pregunta a GitHub. Llamar en segundo plano. Nunca lanza: lo que salga mal es {@code FAILED}.
     */
    public Result check() {
        Result result;
        try {
            result = fetch();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = new Result(Status.FAILED, null, clock.instant());
        } catch (IOException | RuntimeException e) {
            // Sin conexión es lo normal: queda en el registro, sin molestar
            LOG.log(System.Logger.Level.INFO, "No se pudo comprobar si hay versiones nuevas: {0}", e.toString());
            result = new Result(Status.FAILED, null, clock.instant());
        }
        last = result;
        return result;
    }

    private Result fetch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "vn-launcher/" + currentVersion)
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        Instant now = clock.instant();
        if (response.statusCode() == 404) {
            return new Result(Status.UP_TO_DATE, null, now); // aún no hay ninguna release
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub respondió " + response.statusCode());
        }
        JsonNode body = json.readTree(response.body());
        String tag = body.path("tag_name").asText("");
        Optional<int[]> candidate = parse(tag);
        if (candidate.isEmpty()) {
            throw new IOException("Etiqueta de release no reconocida: " + tag);
        }
        if (!isNewer(tag, currentVersion)) {
            return new Result(Status.UP_TO_DATE, null, now);
        }
        return new Result(Status.AVAILABLE, new Release(versionOf(tag), pageOf(body, tag)), now);
    }

    /** La página de la release. Un enlace que no sea de nuestro repositorio no se abre: se usa el de la etiqueta. */
    private static String pageOf(JsonNode body, String tag) {
        String url = body.path("html_url").asText("");
        return url.startsWith(TRUSTED_PREFIX) ? url : RELEASES_PAGE + tag;
    }

    /**
     * Al arrancar: si está activado, comprueba y devuelve la versión nueva solo si aún no se avisó de
     * ella (para el punto de la barra lateral). Llamar en segundo plano.
     */
    public Optional<Release> checkAtStartup() throws SQLException {
        if (!settings.checkUpdatesOnStart()) {
            return Optional.empty();
        }
        Result result = check();
        if (result.status() != Status.AVAILABLE) {
            return Optional.empty();
        }
        boolean alreadyNotified = settings.lastNotifiedUpdate().filter(result.release().version()::equals).isPresent();
        return alreadyNotified ? Optional.empty() : Optional.of(result.release());
    }

    /**
     * Se ha entrado en Ajustes: la versión nueva que haya (la del arranque o la de «Comprobar
     * ahora») ya está avisada y el punto no vuelve hasta que salga otra distinta.
     */
    public void acknowledge() throws SQLException {
        Result result = last;
        if (result != null && result.status() == Status.AVAILABLE) {
            settings.setLastNotifiedUpdate(result.release().version());
        }
    }

    // ------------------------------------------------------------------ versiones

    /**
     * Si {@code candidate} es una versión más nueva que {@code current}. Compara número a número
     * (1.0.10 > 1.0.9), ignora la «v» y lo que falte vale 0 (1.0 = 1.0.0). Si alguna no se entiende,
     * no es más nueva.
     */
    public static boolean isNewer(String candidate, String current) {
        Optional<int[]> a = parse(candidate);
        Optional<int[]> b = parse(current);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        int length = Math.max(a.get().length, b.get().length);
        for (int i = 0; i < length; i++) {
            int x = i < a.get().length ? a.get()[i] : 0;
            int y = i < b.get().length ? b.get()[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    /** «v1.0.1» → «1.0.1» (sin la «v» ni sufijos como «-beta»). */
    static String versionOf(String tag) {
        Matcher m = VERSION.matcher(tag.strip());
        return m.find() ? m.group(1) : tag;
    }

    private static Optional<int[]> parse(String version) {
        if (version == null) {
            return Optional.empty();
        }
        Matcher m = VERSION.matcher(version.strip());
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            String[] parts = m.group(1).split("\\.");
            int[] numbers = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = Integer.parseInt(parts[i]);
            }
            return Optional.of(numbers);
        } catch (NumberFormatException e) { // un número desmesurado
            return Optional.empty();
        }
    }
}

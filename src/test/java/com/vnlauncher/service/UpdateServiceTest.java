package com.vnlauncher.service;

import com.sun.net.httpserver.HttpServer;
import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.service.UpdateService.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T18:07:00Z");

    @TempDir
    Path tempDir;

    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "";
    private SettingsService settings;
    private UpdateService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        settings = new SettingsService(new SettingsRepository(db), ContentFilter.withDefaults());
        service = serviceFor("1.0.0");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private UpdateService serviceFor(String current) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/releases/latest");
        return new UpdateService(HttpClient.newHttpClient(), endpoint, current, settings,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String release(String tag, String url) {
        return "{\"tag_name\":\"" + tag + "\",\"html_url\":\"" + url + "\"}";
    }

    @Test
    void versionsAreComparedNumberByNumberIgnoringTheV() {
        assertTrue(UpdateService.isNewer("1.0.10", "1.0.9"), "10 > 9, no como texto");
        assertTrue(UpdateService.isNewer("v1.0.1", "1.0.0"));
        assertTrue(UpdateService.isNewer("V2.0", "1.9.9"));
        assertTrue(UpdateService.isNewer("1.1", "1.0.5"));
        assertFalse(UpdateService.isNewer("1.0", "1.0.0"), "Lo que falta vale 0");
        assertFalse(UpdateService.isNewer("1.0.0", "v1.0.0"));
        assertFalse(UpdateService.isNewer("0.9.9", "1.0.0"));
        assertFalse(UpdateService.isNewer("nightly", "1.0.0"), "Lo que no se entiende no es más nuevo");
        assertFalse(UpdateService.isNewer("1.0.1", "?"), "Ni con una versión actual desconocida");
        assertTrue(UpdateService.isNewer("1.2.0-beta", "1.1.0"), "El sufijo no cuenta");
        assertFalse(UpdateService.isNewer("99999999999.0", "1.0"), "Un número desmesurado no revienta");
    }

    @Test
    void aNewerReleaseIsAvailableWithItsPage() {
        body = release("v1.0.1", "https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.1");

        UpdateService.Result result = service.check();

        assertEquals(Status.AVAILABLE, result.status());
        assertEquals("1.0.1", result.release().version());
        assertEquals("https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.1", result.release().url());
        assertEquals(NOW, result.checkedAt());
        assertEquals(result, service.lastResult().orElseThrow());
    }

    @Test
    void theSameOrAnOlderReleaseMeansUpToDate() {
        body = release("v1.0.0", "https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.0");
        assertEquals(Status.UP_TO_DATE, service.check().status());
        body = release("v0.9.0", "https://github.com/JulioInAction/vn-launcher/releases/tag/v0.9.0");
        assertEquals(Status.UP_TO_DATE, service.check().status());
    }

    @Test
    void noReleasesYetIsNotAnError() {
        status = 404;
        body = "{\"message\":\"Not Found\"}";

        assertEquals(Status.UP_TO_DATE, service.check().status());
    }

    @Test
    void anythingElseThatGoesWrongIsQuietlyFailed() {
        status = 500;
        assertEquals(Status.FAILED, service.check().status());

        status = 403; // límite de peticiones de GitHub
        assertEquals(Status.FAILED, service.check().status());

        status = 200;
        body = "esto no es json";
        assertEquals(Status.FAILED, service.check().status());

        body = "{\"tag_name\":\"nightly\"}";
        assertEquals(Status.FAILED, service.check().status(), "Una etiqueta que no es una versión");
    }

    @Test
    void withoutAnyConnectionItFailsInsteadOfThrowing() {
        server.stop(0);

        assertEquals(Status.FAILED, service.check().status());
    }

    @Test
    void aLinkThatIsNotOurRepositoryIsNotTrusted() {
        body = release("v1.0.1", "https://malo.example.com/descarga.exe");

        assertEquals("https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.1",
                service.check().release().url());
    }

    @Test
    void atStartupItOnlyChecksWhenTurnedOnAndOnlyNotifiesOnce() throws Exception {
        body = release("v1.0.1", "https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.1");

        settings.setCheckUpdatesOnStart(false);
        assertTrue(service.checkAtStartup().isEmpty(), "Apagado: ni se comprueba");
        assertTrue(service.lastResult().isEmpty());

        settings.setCheckUpdatesOnStart(true);
        assertEquals("1.0.1", service.checkAtStartup().orElseThrow().version());

        service.acknowledge(); // se ha entrado en Ajustes
        assertEquals("1.0.1", settings.lastNotifiedUpdate().orElseThrow());
        assertTrue(service.checkAtStartup().isEmpty(), "La misma versión no vuelve a avisar");

        body = release("v1.0.2", "https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.2");
        assertEquals("1.0.2", service.checkAtStartup().orElseThrow().version(), "Otra distinta, sí");
    }

    @Test
    void acknowledgingWithNothingAvailableSavesNothing() throws Exception {
        body = release("v1.0.0", "https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.0");
        service.check();
        service.acknowledge();

        assertTrue(settings.lastNotifiedUpdate().isEmpty());
    }

    @Test
    void checkingAtStartupIsOnByDefault() throws Exception {
        assertTrue(settings.checkUpdatesOnStart());
    }
}

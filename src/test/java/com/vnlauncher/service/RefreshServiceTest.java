package com.vnlauncher.service;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.CharacterRepository;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.PlaySessionRepository;
import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.db.repo.VnDetailsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.testutil.FakeVndbServer;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** "Actualizar desde VNDB": servicios y repositorios reales, VNDB e imágenes de un servidor falso. */
class RefreshServiceTest {

    private static final Instant ADDED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @TempDir
    Path tempDir;

    private FakeVndbServer server;
    private VnRepository vnRepo;
    private LibraryRepository libraryRepo;
    private RouteService routes;
    private LibraryService library;
    private RefreshService refresh;
    private VnDetailsService details;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeVndbServer();
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        HttpClient http = HttpClient.newHttpClient();
        VndbClient vndb = new VndbClient(http,
                new RateLimiter(100, Duration.ofMinutes(5), InstantSource.system(), d -> { }),
                d -> { }, server.apiBase());
        ImageCacheService images = new ImageCacheService(http, new AppPaths(tempDir));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        vnRepo = new VnRepository(db);
        libraryRepo = new LibraryRepository(db);
        routes = new RouteService(new RouteRepository(db), clock);
        library = new LibraryService(vnRepo, libraryRepo, new RouteRepository(db),
                new PlaySessionRepository(db), images, clock);
        details = new VnDetailsService(vndb, new VnDetailsRepository(db), clock);
        refresh = new RefreshService(new CatalogService(vndb), library,
                new CharacterService(vndb, new CharacterRepository(db), images, clock), details);

        // Estado de partida: CLANNAD en la biblioteca con los datos "viejos", su portada vieja
        // descargada, un ejecutable elegido y una ruta.
        VisualNovel old = new VisualNovel("v4", "Clannad (viejo)", null, "Descripción vieja", "2004",
                server.baseUrl() + "/cv/old.jpg", null, 0.0, 0.0, List.of());
        vnRepo.save(old, ADDED);
        libraryRepo.add("v4", ADDED);
        library.downloadMissingCovers();
        libraryRepo.updateExePath("v4", "C:\\Juegos\\CLANNAD\\RealLive.exe");
        routes.addRoute("v4", null, "After Story", "Mis notas");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private String vnResponse(String title, String imagePath) {
        return """
                {"results": [{"id": "v4", "title": "%s", "alttitle": "クラナド", "released": "2004-04-28",
                  "description": "Nueva [b]descripción[/b]",
                  "image": {"url": "%s", "sexual": 0.0, "violence": 0.0},
                  "developers": [{"id": "p24", "name": "Key", "original": null}]}],
                 "more": false}
                """.formatted(title, server.baseUrl() + imagePath);
    }

    private String charactersResponse() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/com/vnlauncher/api/character-v4.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("https://t.vndb.org", server.baseUrl());
        }
    }

    private static final String TAGS_RESPONSE = """
            {"results": [{"id": "v4",
              "tags": [{"id": "g1", "name": "Drama", "rating": 2.9, "spoiler": 0, "lie": false, "category": "cont"}],
              "screenshots": [{"url": "https://t.vndb.org/sf/1.jpg", "thumbnail": "https://t.vndb.org/st/1.jpg",
                               "sexual": 0, "violence": 0}]}],
             "more": false}
            """;

    @Test
    void updatesDataCoverCharactersAndTagsButKeepsUserData() throws Exception {
        server.enqueue(vnResponse("CLANNAD", "/cv/new.jpg"));
        server.enqueue(charactersResponse());
        server.enqueue(TAGS_RESPONSE);

        RefreshService.Result result = refresh.refresh("v4");

        assertTrue(result.found());
        assertEquals(2, result.characters(), "Nagisa y Ushio, como en CharacterServiceTest");
        assertEquals(3, server.apiRequests().size(), "1 petición de la VN + 1 de personajes + 1 de etiquetas");
        assertTrue(server.apiRequests().get(2).contains("tags.spoiler"), server.apiRequests().get(2));
        assertEquals(List.of("Drama"), details.get("v4").tags().stream().map(t -> t.name()).toList(),
                "Etiquetas guardadas: la ficha ya no las pide");
        assertEquals(3, server.apiRequests().size());
        assertTrue(server.apiRequests().getFirst().contains("[\"id\",\"=\",\"v4\"]"),
                "Se pide la VN por su id: " + server.apiRequests().getFirst());

        VisualNovel saved = vnRepo.findById("v4").orElseThrow();
        assertEquals("CLANNAD", saved.title());
        assertEquals("Nueva descripción", saved.description(), "Descripción limpia de formato");
        assertEquals("Key", saved.developerNames());
        assertEquals(NOW, library.fetchedAt("v4").orElseThrow(), "Fecha de los datos actualizada");
        assertTrue(server.imageRequests().contains("/cv/new.jpg"), "La portada nueva se descarga");

        // Lo del usuario no se toca
        assertEquals("C:\\Juegos\\CLANNAD\\RealLive.exe", libraryRepo.findByVnId("v4").orElseThrow().exePath());
        List<Route> kept = routes.routesOf("v4");
        assertEquals(1, kept.size());
        assertEquals("Mis notas", kept.getFirst().notes());
    }

    @Test
    void sameCoverUrlIsNotDownloadedAgain() throws Exception {
        server.enqueue(vnResponse("CLANNAD", "/cv/old.jpg"));
        server.enqueue("{\"results\": [], \"more\": false}");
        server.enqueue("{\"results\": [], \"more\": false}");
        int imagesBefore = server.imageRequests().size();

        refresh.refresh("v4");

        assertEquals(imagesBefore, server.imageRequests().size(), "La portada no ha cambiado: 0 descargas");
    }

    @Test
    void vnThatNoLongerExistsInVndbKeepsItsData() throws Exception {
        server.enqueue("{\"results\": [], \"more\": false}");

        RefreshService.Result result = refresh.refresh("v4");

        assertFalse(result.found());
        assertEquals(1, server.apiRequests().size(), "Sin VN no se piden personajes ni etiquetas");
        assertEquals("Clannad (viejo)", vnRepo.findById("v4").orElseThrow().title());
        assertEquals(ADDED, library.fetchedAt("v4").orElseThrow());
    }
}

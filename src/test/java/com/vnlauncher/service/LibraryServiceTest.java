package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.PlaySessionRepository;
import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.testutil.FakeImageServer;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Test de integración: servicio + repositorios reales sobre una BD temporal + servidor de imágenes falso. */
class LibraryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @TempDir
    Path tempDir;

    private FakeImageServer server;
    private ImageCacheService images;
    private LibraryService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeImageServer();
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        images = new ImageCacheService(HttpClient.newHttpClient(), new AppPaths(tempDir));
        // Clock.fixed: un reloj parado, para saber exactamente qué fecha se guardará.
        library = new LibraryRepository(db);
        routes = new RouteService(new RouteRepository(db), Clock.fixed(NOW, ZoneOffset.UTC));
        sessions = new PlaySessionRepository(db);
        service = new LibraryService(new VnRepository(db), library, new RouteRepository(db), sessions,
                images, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LibraryRepository library;
    private RouteService routes;
    private PlaySessionRepository sessions;

    @AfterEach
    void tearDown() {
        server.close();
    }

    private VisualNovel searchResult(String id, String imageUrl) {
        // Como un resultado de búsqueda: con URL de portada pero sin imagePath
        return new VisualNovel(id, "Título " + id, null, null, null, imageUrl, null, 0.0, 0.0, List.of());
    }

    @Test
    void addToLibrarySavesVnAndDownloadsCover() throws Exception {
        service.addToLibrary(searchResult("v4", server.url("/cv/95/75895.jpg")));

        LibraryEntry entry = service.find("v4").orElseThrow();
        assertEquals(NOW, entry.addedAt());
        assertEquals("images/vn/v4.jpg", entry.vn().imagePath());
        assertTrue(images.isCached(entry.vn().imagePath()));
    }

    @Test
    void failedCoverDownloadStillAddsVnAndCanBeRetried() throws Exception {
        server.respondWith(404);
        service.addToLibrary(searchResult("v4", server.url("/cv/1.jpg")));

        List<LibraryEntry> all = service.findAll();
        assertEquals(1, all.size(), "La VN se añade aunque falle la portada");
        assertTrue(service.hasMissingCovers(all));

        server.respondWith(200);
        assertEquals(1, service.downloadMissingCovers());
        assertFalse(service.hasMissingCovers(service.findAll()));
    }

    @Test
    void downloadMissingCoversReportsProgress() throws Exception {
        server.respondWith(404);
        service.addToLibrary(searchResult("v1", server.url("/cv/1.jpg")));
        service.addToLibrary(searchResult("v2", server.url("/cv/2.jpg")));
        service.addToLibrary(searchResult("v3", null)); // sin portada: no cuenta
        server.respondWith(200);

        List<String> steps = new java.util.ArrayList<>();
        int downloaded = service.downloadMissingCovers((done, total) -> steps.add(done + "/" + total));

        assertEquals(2, downloaded);
        assertEquals(List.of("0/2", "1/2", "2/2"), steps);
    }

    @Test
    void deletedCoverFileIsDownloadedAgain() throws Exception {
        service.addToLibrary(searchResult("v4", server.url("/cv/1.jpg")));
        Files.delete(images.resolve("images/vn/v4.jpg")); // el usuario borra la carpeta de imágenes

        assertTrue(service.hasMissingCovers(service.findAll()));
        assertEquals(1, service.downloadMissingCovers());
        assertEquals(2, server.requestCount());
    }

    @Test
    void vnWithoutCoverIsNotConsideredMissing() throws Exception {
        service.addToLibrary(searchResult("v1", null));

        assertFalse(service.hasMissingCovers(service.findAll()));
        assertEquals(0, server.requestCount());
    }

    @Test
    void removeTakesVnOutOfLibrary() throws Exception {
        service.addToLibrary(searchResult("v4", null));
        assertTrue(service.isInLibrary("v4"));

        service.remove("v4");

        assertFalse(service.isInLibrary("v4"));
    }

    @Test
    void statusIsComputedFromRoutesAndLastPlayed() throws Exception {
        service.addToLibrary(searchResult("v1", null)); // nada: pendiente
        service.addToLibrary(searchResult("v2", null)); // una ruta en curso: jugando
        service.addToLibrary(searchResult("v3", null)); // todas completadas
        service.addToLibrary(searchResult("v4", null)); // lanzada sin rutas: jugando
        routes.setStatus(routes.addRoute("v2", null, "A", null).id(), RouteStatus.IN_PROGRESS);
        routes.setStatus(routes.addRoute("v3", null, "A", null).id(), RouteStatus.COMPLETED);
        library.updateLastPlayed("v4", NOW);

        var byId = new java.util.HashMap<String, PlayStatus>();
        for (LibraryItem item : service.findAllWithStatus()) {
            byId.put(item.entry().vn().id(), item.status());
        }

        assertEquals(PlayStatus.PENDING, byId.get("v1"));
        assertEquals(PlayStatus.PLAYING, byId.get("v2"));
        assertEquals(PlayStatus.COMPLETED, byId.get("v3"));
        assertEquals(PlayStatus.PLAYING, byId.get("v4"));
    }

    @Test
    void theStatusSetByHandWinsAndIsCountedThere() throws Exception {
        for (String id : List.of("v1", "v2", "v3")) {
            service.addToLibrary(searchResult(id, null));
        }
        library.updateLastPlayed("v1", NOW);   // jugando...
        service.setStatus("v1", PlayStatus.PAUSED); // ...pero en pausa a mano
        service.setStatus("v2", PlayStatus.DROPPED);

        var items = new java.util.HashMap<String, LibraryItem>();
        for (LibraryItem item : service.findAllWithStatus()) {
            items.put(item.entry().vn().id(), item);
        }
        assertEquals(PlayStatus.PAUSED, items.get("v1").status());
        assertEquals(PlayStatus.PLAYING, items.get("v1").automaticStatus(), "El calculado sigue ahí");
        assertTrue(items.get("v1").manualStatus());
        assertEquals(PlayStatus.DROPPED, items.get("v2").status());
        assertFalse(items.get("v3").manualStatus());
        // Lo que cuenta la barra lateral: cada VN en un solo estado
        var counts = new java.util.EnumMap<PlayStatus, Long>(PlayStatus.class);
        items.values().forEach(i -> counts.merge(i.status(), 1L, Long::sum));
        assertEquals(java.util.Map.of(PlayStatus.PAUSED, 1L, PlayStatus.DROPPED, 1L, PlayStatus.PENDING, 1L), counts);

        service.setStatus("v1", null); // «Automático»
        assertEquals(PlayStatus.PLAYING, service.findAllWithStatus().stream()
                .filter(i -> i.entry().vn().id().equals("v1")).findFirst().orElseThrow().status());
    }

    @Test
    void recentlyPlayedIsSortedNewestFirstAndLimited() throws Exception {
        for (String id : List.of("v1", "v2", "v3", "v4")) {
            service.addToLibrary(searchResult(id, null));
        }
        library.updateLastPlayed("v1", NOW.minusSeconds(300));
        library.updateLastPlayed("v2", NOW);
        library.updateLastPlayed("v3", NOW.minusSeconds(100));
        library.updateLastPlayed("v4", NOW.minusSeconds(900));

        List<LibraryEntry> recent = LibraryService.recentlyPlayed(service.findAllWithStatus(), 3);

        assertEquals(List.of("v2", "v3", "v1"), recent.stream().map(e -> e.vn().id()).toList());
    }
}

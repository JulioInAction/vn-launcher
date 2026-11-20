package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.LibraryEntry;
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
        service = new LibraryService(new VnRepository(db), new LibraryRepository(db), images,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

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
}

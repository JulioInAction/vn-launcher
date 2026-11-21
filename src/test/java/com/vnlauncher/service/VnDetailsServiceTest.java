package com.vnlauncher.service;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.dto.ScreenshotDto;
import com.vnlauncher.api.dto.TagDto;
import com.vnlauncher.api.dto.VnDetailsDto;
import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.VnDetailsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnDetails;
import com.vnlauncher.model.VnTag;
import com.vnlauncher.testutil.FakeVndbServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Etiquetas y capturas de la ficha: se piden una vez y después salen de la base de datos. */
class VnDetailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @TempDir
    Path tempDir;

    private FakeVndbServer server;
    private VnDetailsService service;
    private LibraryRepository library;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeVndbServer();
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        VndbClient vndb = new VndbClient(HttpClient.newHttpClient(),
                new RateLimiter(100, Duration.ofMinutes(5), InstantSource.system(), d -> { }),
                d -> { }, server.apiBase());
        service = new VnDetailsService(vndb, new VnDetailsRepository(db), Clock.fixed(NOW, ZoneOffset.UTC));
        library = new LibraryRepository(db);
        VnRepository vns = new VnRepository(db);
        for (String id : List.of("v4", "v11", "v17")) {
            vns.save(new VisualNovel(id, "VN " + id, null, null, null, null, null, null, null, List.of()), NOW);
            library.add(id, NOW);
        }
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static String response(String vnId, String tagsJson) {
        return """
                {"results": [{"id": "%s", "tags": [%s],
                  "screenshots": [
                    {"url": "https://t.vndb.org/sf/1.jpg", "thumbnail": "https://t.vndb.org/st/1.jpg", "sexual": 0, "violence": 0},
                    {"url": "https://t.vndb.org/sf/2.jpg", "thumbnail": "https://t.vndb.org/st/2.jpg", "sexual": 1.8, "violence": 0}]}],
                 "more": false}
                """.formatted(vnId, tagsJson);
    }

    private static final String DRAMA = """
            {"id": "g1", "name": "Drama", "rating": 2.5, "spoiler": 0, "lie": false, "category": "cont"}""";
    private static final String TWIST = """
            {"id": "g2", "name": "Giro final", "rating": 2.9, "spoiler": 2, "lie": false, "category": "cont"}""";

    @Test
    void firstTimeAsksVndbAndThenReadsFromTheDatabase() throws Exception {
        server.enqueue(response("v4", DRAMA + "," + TWIST));

        VnDetails first = service.get("v4");
        VnDetails second = service.get("v4");

        assertEquals(1, server.apiRequests().size(), "La segunda vez sale de la base de datos (sin conexión también)");
        String body = server.apiRequests().getFirst();
        assertTrue(body.contains("[\"id\",\"=\",\"v4\"]"), body);
        assertTrue(body.contains("tags.spoiler") && body.contains("screenshots.thumbnail")
                && body.contains("screenshots.sexual"), body);
        assertFalse(body.contains("relations"), "La ficha no necesita relaciones: " + body);

        assertEquals(first, second);
        assertEquals(List.of("Giro final", "Drama"), second.tags().stream().map(VnTag::name).toList(),
                "De mayor a menor puntuación; las spoiler también se guardan (la ficha decide)");
        assertTrue(second.tags().getFirst().isSpoiler());
        assertEquals(2, second.screenshots().size());
        assertEquals("https://t.vndb.org/st/1.jpg", second.screenshots().getFirst().thumbnailUrl());
        assertEquals(1.8, second.screenshots().get(1).sexual());
    }

    @Test
    void refreshAsksAgainAndReplacesWhatWasSaved() throws Exception {
        server.enqueue(response("v4", DRAMA + "," + TWIST));
        server.enqueue(response("v4", DRAMA));
        service.get("v4");

        VnDetails refreshed = service.refresh("v4");

        assertEquals(2, server.apiRequests().size());
        assertEquals(List.of("Drama"), refreshed.tags().stream().map(VnTag::name).toList());
    }

    @Test
    void vnMissingInVndbIsSavedEmptySoItIsNotAskedAgain() throws Exception {
        server.enqueue("{\"results\": [], \"more\": false}");

        assertEquals(VnDetails.EMPTY, service.get("v4"));
        assertEquals(VnDetails.EMPTY, service.get("v4"));
        assertEquals(1, server.apiRequests().size());
    }

    @Test
    void liesAndIncompleteTagsAreDropped() {
        VnDetailsDto dto = new VnDetailsDto("v4",
                List.of(new TagDto("g1", "Drama", 2.0, 0, false, "cont"),
                        new TagDto("g2", "Mentira", 3.0, 0, true, "cont"),
                        new TagDto("g3", null, 3.0, 0, false, "cont"),
                        new TagDto("g4", "Escenas sexuales", 2.2, null, null, "ero")),
                List.of(new ScreenshotDto("https://t.vndb.org/sf/1.jpg", null, null, null),
                        new ScreenshotDto(null, "https://t.vndb.org/st/2.jpg", 0.0, 0.0)),
                null);

        VnDetails details = VnDetailsService.toModel(dto);

        assertEquals(List.of("Escenas sexuales", "Drama"), details.tags().stream().map(VnTag::name).toList());
        assertEquals(0, details.tags().getFirst().spoiler(), "Sin spoiler = 0");
        assertTrue(details.tags().getFirst().isSexual());
        assertEquals(1, details.screenshots().size(), "Una captura sin URL no sirve");
        assertNull(details.screenshots().getFirst().thumbnailUrl());
    }

    @Test
    void libraryCanBeFilteredByTagAndKnowsWhichVnsHaveNoTagsYet() throws Exception {
        server.enqueue(response("v4", DRAMA));
        server.enqueue(response("v11", DRAMA + "," + TWIST));
        service.get("v4");
        service.get("v11");

        assertEquals(Set.of("v4", "v11"), service.vnIdsWithTag("g1"));
        assertEquals(Set.of("v11"), service.vnIdsWithTag("g2"));
        assertEquals(Set.of(), service.vnIdsWithTag("g99"));
        assertEquals(1, service.countLibraryWithoutTags(), "v17: nunca se ha abierto su ficha");
    }
}

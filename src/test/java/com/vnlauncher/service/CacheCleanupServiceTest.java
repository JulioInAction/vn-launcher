package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.CharacterRepository;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.db.repo.VnDetailsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.CharacterRole;
import com.vnlauncher.model.Developer;
import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.model.VnDetails;
import com.vnlauncher.model.VnScreenshot;
import com.vnlauncher.model.VnTag;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** La caché de las VNs vistas en la búsqueda y no añadidas se limpia a los 30 días. */
class CacheCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @TempDir
    Path tempDir;

    private Database db;
    private AppPaths paths;
    private VnRepository vns;
    private VnDetailsRepository details;
    private CharacterRepository characters;
    private LibraryRepository library;
    private CacheCleanupService service;

    @BeforeEach
    void setUp() throws Exception {
        paths = new AppPaths(tempDir);
        paths.createDirectories();
        db = new Database(paths.databaseFile());
        db.migrate();
        vns = new VnRepository(db);
        details = new VnDetailsRepository(db);
        characters = new CharacterRepository(db);
        library = new LibraryRepository(db);
        service = new CacheCleanupService(db, new ImageCacheService(HttpClient.newHttpClient(), paths),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static VisualNovel vn(String id) {
        return new VisualNovel(id, "VN " + id, null, null, "2004", "https://t.vndb.org/cv/1/" + id + ".jpg",
                "images/vn/" + id + ".jpg", 0.0, 0.0, List.of(new Developer("p" + id, "Dev " + id, null)));
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private Path file(String relative) throws Exception {
        Path file = paths.root().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
        return file;
    }

    private int count(String table) throws Exception {
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getInt(1);
        }
    }

    private void saveWithDetails(VisualNovel vn, Instant viewed) throws Exception {
        vns.save(vn, viewed);
        vns.updateImagePath(vn.id(), vn.imagePath()); // la portada ya descargada
        details.saveForVn(vn.id(), new VnDetails(
                List.of(new VnTag("g1", "Drama", 2.0, 0, "cont")),
                List.of(new VnScreenshot("https://t.vndb.org/sf/03/12303.jpg", "https://t.vndb.org/st/03/12303.jpg",
                        0.0, 0.0))), viewed);
    }

    @Test
    void aViewedVnIsKeptForThirtyDaysAndThenGoesWithEverythingItOwns() throws Exception {
        saveWithDetails(vn("v1"), daysAgo(31));
        Path cover = file("images/vn/v1.jpg");
        Path thumb = file("images/shots/st-03-12303.jpg");
        Path full = file("images/shots/sf-03-12303.jpg");

        CacheCleanupService.Result result = service.cleanUp();

        assertEquals(1, result.visualNovels());
        assertEquals(0, count("vn"));
        assertEquals(0, count("vn_tag"));
        assertEquals(0, count("vn_screenshot"));
        assertEquals(0, count("vn_developer"));
        assertEquals(0, count("developer"), "El desarrollador se queda sin ninguna VN");
        assertFalse(Files.exists(cover) || Files.exists(thumb) || Files.exists(full));
    }

    @Test
    void aRecentVnIsLeftAlone() throws Exception {
        saveWithDetails(vn("v1"), daysAgo(29));
        file("images/vn/v1.jpg");

        assertTrue(service.cleanUp().isEmpty());

        assertEquals(1, count("vn"));
        assertEquals(1, count("vn_tag"));
        assertTrue(Files.exists(paths.root().resolve("images/vn/v1.jpg")));
    }

    @Test
    void lookingAtItAgainRestartsTheThirtyDays() throws Exception {
        vns.save(vn("v1"), daysAgo(90));
        vns.markViewed("v1", daysAgo(2));

        assertTrue(service.cleanUp().isEmpty());
        assertEquals(1, count("vn"));
    }

    @Test
    void aVnInTheLibraryIsNeverDeletedHoweverOldItIs() throws Exception {
        saveWithDetails(vn("v1"), daysAgo(400));
        library.add("v1", daysAgo(400));
        file("images/vn/v1.jpg");

        assertTrue(service.cleanUp().isEmpty());

        assertEquals(1, count("vn"));
        assertEquals(1, count("vn_tag"));
        assertTrue(Files.exists(paths.root().resolve("images/vn/v1.jpg")));
    }

    @Test
    void characterOfADeletedVnGoWithItButNotOnesThatHaveARoute() throws Exception {
        vns.save(vn("v1"), daysAgo(40));
        vns.save(vn("v2"), daysAgo(1));
        library.add("v2", daysAgo(1));
        GameCharacter alone = new GameCharacter("c1", "Sola", null, null, null, "images/char/c1.jpg", 0.0, 0.0);
        GameCharacter withRoute = new GameCharacter("c2", "Con ruta", null, null, null, "images/char/c2.jpg", 0.0, 0.0);
        GameCharacter shared = new GameCharacter("c3", "Compartida", null, null, null, "images/char/c3.jpg", 0.0, 0.0);
        characters.saveForVn("v1", List.of(new VnCharacter(alone, CharacterRole.PRIMARY, 0),
                new VnCharacter(withRoute, CharacterRole.PRIMARY, 0),
                new VnCharacter(shared, CharacterRole.PRIMARY, 0)), daysAgo(40));
        characters.saveForVn("v2", List.of(new VnCharacter(shared, CharacterRole.PRIMARY, 0)), daysAgo(1));
        // Una ruta de la VN de la biblioteca con la heroína que solo salía en la VN que se va
        new RouteRepository(db).insert(new Route(null, "v2", "c2", "Ruta de c2", 1, RouteStatus.PENDING, null, null));
        for (GameCharacter c : List.of(alone, withRoute, shared)) {
            characters.updateImagePath(c.id(), c.imagePath());
        }
        Path aloneImage = file("images/char/c1.jpg");
        Path routeImage = file("images/char/c2.jpg");
        Path sharedImage = file("images/char/c3.jpg");

        CacheCleanupService.Result result = service.cleanUp();

        assertEquals(new CacheCleanupService.Result(1, 1), result);
        assertEquals(List.of("c2", "c3"), characterIds());
        assertFalse(Files.exists(aloneImage));
        assertTrue(Files.exists(routeImage), "La imagen de un personaje con ruta se queda");
        assertTrue(Files.exists(sharedImage));
        // Y la ruta sigue apuntando a su personaje
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT character_id FROM route")) {
            assertEquals("c2", rs.getString(1));
        }
    }

    @Test
    void aVnWithoutViewDateCountsFromWhenItWasFetched() throws Exception {
        vns.save(vn("v1"), daysAgo(60));
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
            st.execute("UPDATE vn SET last_viewed_at = NULL");
        }

        assertEquals(1, service.cleanUp().visualNovels());
    }

    @Test
    void aMissingFileDoesNotStopTheCleanUp() throws Exception {
        saveWithDetails(vn("v1"), daysAgo(31)); // sin ninguna imagen en disco

        assertEquals(1, service.cleanUp().visualNovels());
        assertEquals(0, count("vn"));
    }

    private List<String> characterIds() throws Exception {
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM character ORDER BY id")) {
            List<String> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
            return ids;
        }
    }
}

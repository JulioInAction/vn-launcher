package com.vnlauncher.service;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.dto.LanguageDto;
import com.vnlauncher.api.dto.ReleaseDto;
import com.vnlauncher.api.dto.ReleaseImageDto;
import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.CoverOption;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.testutil.FakeVndbServer;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @TempDir
    Path tempDir;

    private FakeVndbServer server;
    private AppPaths paths;
    private LibraryRepository library;
    private ImageCacheService images;
    private CoverService covers;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeVndbServer();
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        paths = new AppPaths(tempDir);
        HttpClient http = HttpClient.newHttpClient();
        VndbClient vndb = new VndbClient(http,
                new RateLimiter(100, Duration.ofMinutes(5), InstantSource.system(), d -> { }),
                d -> { }, server.apiBase());
        images = new ImageCacheService(http, paths);
        library = new LibraryRepository(db);
        covers = new CoverService(vndb, library, images, paths, Clock.fixed(NOW, ZoneOffset.UTC));

        // CLANNAD en la biblioteca con su portada de VNDB ya descargada
        new VnRepository(db).save(new VisualNovel("v4", "CLANNAD", null, null, null,
                server.baseUrl() + "/cv/main.jpg", null, 0.0, 0.0, List.of()), NOW);
        library.add("v4", NOW);
        String main = images.download(server.baseUrl() + "/cv/main.jpg", "vn", "v4");
        new VnRepository(db).updateImagePath("v4", main);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private LibraryEntry entry() throws Exception {
        return library.findByVnId("v4").orElseThrow();
    }

    private static ReleaseImageDto image(String id, String type, List<String> langs, String vn) {
        return new ReleaseImageDto(id, "https://t.vndb.org/cv/" + id + ".jpg",
                "https://t.vndb.org/cv.t/" + id + ".jpg", type, langs, vn, 0.0, 0.0);
    }

    // ---------------------------------------------------------------- galería

    @Test
    void optionsKeepOnlyThisVnWithoutRepeatsAndFrontCoversFirst() {
        List<ReleaseDto> releases = List.of(
                new ReleaseDto("r1", "CLANNAD", "2004-04-28", List.of(new LanguageDto("ja")), List.of(
                        image("cv1", "pkgback", null, null),
                        image("cv2", "pkgfront", null, null))),
                new ReleaseDto("r2", "CLANNAD + Tomoyo After", "2010", List.of(new LanguageDto("ja")), List.of(
                        image("cv3", "pkgfront", null, "v12"),       // de otra VN del pack: fuera
                        image("cv2", "pkgfront", null, null))),      // repetida: fuera
                new ReleaseDto("r3", "CLANNAD (Steam)", "2015", List.of(new LanguageDto("en")), List.of(
                        image("cv4", "dig", List.of("en"), "v4"))),
                new ReleaseDto("r4", "Sin imágenes", "2016", List.of(), null));

        List<CoverOption> options = CoverService.toOptions(releases, "v4");

        assertEquals(List.of("cv2", "cv4", "cv1"), options.stream().map(CoverOption::imageId).toList(),
                "Caja frontal, digital y luego la trasera");
        assertEquals("JA · Caja frontal", options.get(0).subtitle(), "Idioma de la edición si la imagen no lo dice");
        assertEquals("EN · Digital", options.get(1).subtitle());
        assertEquals("CLANNAD (Steam)", options.get(1).releaseTitle());
        assertTrue(options.get(0).thumbnailUrl().contains("/cv.t/"), "Se usa la miniatura");
    }

    @Test
    void optionsAreOneRequestToReleaseFilteredByTheVn() throws Exception {
        server.enqueue("""
                {"results": [{"id": "r1", "title": "CLANNAD", "released": "2004-04-28",
                  "languages": [{"lang": "ja"}],
                  "images": [{"id": "cv82903", "url": "https://t.vndb.org/cv/03/82903.jpg",
                              "thumbnail": "https://t.vndb.org/cv.t/03/82903.jpg", "type": "pkgfront",
                              "languages": null, "vn": null, "sexual": 0, "violence": 0,
                              "dims": [562, 800], "photo": false}]}],
                 "more": false}
                """);

        List<CoverOption> options = covers.vndbOptions("v4");

        assertEquals(1, options.size());
        assertEquals(1, server.apiRequests().size(), "Una sola petición");
        String body = server.apiRequests().getFirst();
        assertTrue(body.contains("[\"vn\",\"=\",[\"id\",\"=\",\"v4\"]]"), body);
        assertTrue(body.contains("images.thumbnail") && body.contains("images.languages"), body);
    }

    @Test
    void thumbnailIsDownloadedOnlyOnce() throws Exception {
        CoverOption option = new CoverOption("cv9", server.baseUrl() + "/cv/9.jpg", server.baseUrl() + "/cv.t/9.jpg",
                "CLANNAD", "ja", "pkgfront", 0.0, 0.0);

        Path first = covers.thumbnail(option);
        covers.thumbnail(option);

        assertTrue(Files.exists(first));
        assertEquals(1, server.imageRequests().stream().filter(p -> p.equals("/cv.t/9.jpg")).count());
        assertFalse(server.imageRequests().contains("/cv/9.jpg"), "La grande no se descarga al mirar la galería");
    }

    // ---------------------------------------------------------------- elegir

    @Test
    void choosingAVndbImageDownloadsTheBigOneAndReplacesThePrevious() throws Exception {
        CoverOption a = new CoverOption("cv1", server.baseUrl() + "/cv/1.jpg", server.baseUrl() + "/cv.t/1.jpg",
                "CLANNAD", "ja", "pkgfront", 0.0, 0.0);
        CoverOption b = new CoverOption("cv2", server.baseUrl() + "/cv/2.jpg", server.baseUrl() + "/cv.t/2.jpg",
                "CLANNAD", "ja", "pkgback", 0.0, 0.0);

        covers.useVndbImage("v4", a);
        assertEquals("covers/v4-cv1.jpg", entry().coverOverride());
        assertEquals(tempDir.resolve("covers/v4-cv1.jpg"), covers.effectiveCover(entry()));
        assertTrue(CoverService.isCurrent(entry(), a));

        covers.useVndbImage("v4", b);
        assertFalse(Files.exists(tempDir.resolve("covers/v4-cv1.jpg")), "La anterior ya no hace falta");
        assertTrue(CoverService.isCurrent(entry(), b));
        assertFalse(CoverService.isCurrent(entry(), a));
    }

    @Test
    void ownFileIsCopiedAndBackToVndbClearsIt() throws Exception {
        Path mine = Files.writeString(tempDir.resolve("Mi Portada.PNG"), "png falso");

        covers.useFile("v4", mine);

        String override = entry().coverOverride();
        assertEquals("covers/v4-propia-" + NOW.toEpochMilli() + ".png", override);
        assertTrue(CoverService.isCustom(entry()));
        Files.delete(mine);
        assertTrue(Files.exists(tempDir.resolve(override)), "Es una copia: no depende del original");

        covers.resetToVndb("v4");

        assertNull(entry().coverOverride());
        assertFalse(Files.exists(tempDir.resolve(override)));
        assertEquals(tempDir.resolve("images/vn/v4.jpg"), covers.effectiveCover(entry()), "Vuelve la de VNDB");
    }

    @Test
    void missingChosenFileFallsBackToTheVndbCover() throws Exception {
        library.updateCoverOverride("v4", "covers/v4-borrada.jpg");

        assertEquals(tempDir.resolve("images/vn/v4.jpg"), covers.effectiveCover(entry()));
        assertFalse(covers.hasOverride(entry()));
    }

    @Test
    void clearingTheImageCacheKeepsTheChosenCover() throws Exception {
        covers.useVndbImage("v4", new CoverOption("cv1", server.baseUrl() + "/cv/1.jpg",
                server.baseUrl() + "/cv.t/1.jpg", "CLANNAD", "ja", "pkgfront", 0.0, 0.0));

        images.clearCache();

        assertEquals(tempDir.resolve("covers/v4-cv1.jpg"), covers.effectiveCover(entry()),
                "covers/ está fuera de images/: «Vaciar caché» no la borra");
    }

    @Test
    void onlyFilesReallyInsideCoversCountAsOwnCovers() {
        java.nio.file.Path root = java.nio.file.Path.of("/datos");
        assertEquals(java.util.Optional.of(java.nio.file.Path.of("/datos/covers/v4-cv1.jpg")),
                CoverService.customCoverFile(root::resolve, "covers/v4-cv1.jpg"));
        assertTrue(CoverService.customCoverFile(root::resolve, "covers/../library.db").isEmpty());
        assertTrue(CoverService.customCoverFile(root::resolve, "images/vn/v4.jpg").isEmpty(), "La caché, no");
        assertTrue(CoverService.customCoverFile(root::resolve, "covers/sub/v4.jpg").isEmpty());
        assertTrue(CoverService.customCoverFile(root::resolve, null).isEmpty());
        assertTrue(CoverService.customCoverFile(root::resolve, "covers/mal\u0000.jpg").isEmpty());
    }
}

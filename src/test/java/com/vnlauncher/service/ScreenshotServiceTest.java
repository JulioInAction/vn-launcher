package com.vnlauncher.service;

import com.vnlauncher.model.VnScreenshot;
import com.vnlauncher.testutil.FakeVndbServer;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ScreenshotServiceTest {

    @TempDir
    Path tempDir;

    private FakeVndbServer server;
    private ImageCacheService images;
    private ScreenshotService shots;
    private VnScreenshot shot;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeVndbServer();
        images = new ImageCacheService(HttpClient.newHttpClient(), new AppPaths(tempDir));
        shots = new ScreenshotService(images);
        shot = new VnScreenshot(server.baseUrl() + "/sf/03/12303.jpg", server.baseUrl() + "/st/03/12303.jpg",
                0.0, 0.0);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void fileNamesComeFromTheUrlSoThumbnailAndImageDoNotClash() {
        assertEquals("sf-03-12303", ScreenshotService.cacheName("https://t.vndb.org/sf/03/12303.jpg"));
        assertEquals("st-03-12303", ScreenshotService.cacheName("https://t.vndb.org/st/03/12303.jpg"));
        assertEquals("a-b-c", ScreenshotService.cacheName("https://x.org/a/b/c"));
    }

    @Test
    void thumbnailIsDownloadedOnceAndTheBigImageOnlyWhenAsked() throws Exception {
        assertTrue(shots.cachedThumbnail(shot).isEmpty());

        Path thumb = shots.thumbnail(shot);
        shots.thumbnail(shot);

        assertEquals(tempDir.resolve("images/shots/st-03-12303.jpg"), thumb);
        assertTrue(Files.exists(thumb));
        assertEquals(List.of("/st/03/12303.jpg"), server.imageRequests(), "Una vez, y la grande no");
        assertEquals(thumb, shots.cachedThumbnail(shot).orElseThrow());
        assertTrue(shots.cachedImage(shot).isEmpty());

        Path big = shots.image(shot);

        assertEquals(tempDir.resolve("images/shots/sf-03-12303.jpg"), big);
        assertEquals(2, server.imageRequests().size());
    }

    @Test
    void withoutThumbnailTheBigImageIsUsedAndDownloadedOnlyOnce() throws Exception {
        VnScreenshot noThumb = new VnScreenshot(server.baseUrl() + "/sf/01/1.jpg", null, null, null);

        assertEquals(shots.thumbnail(noThumb), shots.image(noThumb));
        assertEquals(1, server.imageRequests().size());
    }

    @Test
    void theSameImageAskedFromSeveralThreadsIsDownloadedOnce() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<Path>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> shots.image(shot)));
            }
            for (Future<Path> result : results) {
                assertTrue(Files.exists(result.get()));
            }
        }
        assertEquals(1, server.imageRequests().size(), "La galería y el visor a la vez: una sola descarga");
    }

    @Test
    void clearingTheCacheRemovesThemAndTheyAreDownloadedAgain() throws Exception {
        shots.thumbnail(shot);
        images.clearCache();

        assertTrue(shots.cachedThumbnail(shot).isEmpty());
        shots.thumbnail(shot);
        assertEquals(2, server.imageRequests().size());
    }
}

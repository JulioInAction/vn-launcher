package com.vnlauncher.service;

import com.vnlauncher.testutil.FakeImageServer;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageCacheServiceTest {

    @TempDir
    Path tempDir;

    private FakeImageServer server;
    private ImageCacheService cache;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeImageServer();
        cache = new ImageCacheService(HttpClient.newHttpClient(), new AppPaths(tempDir));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void downloadsToRelativePathAndSkipsSecondTime() throws Exception {
        String url = server.url("/cv/95/75895.jpg");

        String relative = cache.download(url, "vn", "v4");

        assertEquals("images/vn/v4.jpg", relative);
        assertArrayEquals(FakeImageServer.IMAGE_BYTES, Files.readAllBytes(tempDir.resolve(relative)));
        assertTrue(cache.isCached(relative));

        cache.download(url, "vn", "v4");
        assertEquals(1, server.requestCount(), "Si ya existe el fichero no se vuelve a descargar");
    }

    @Test
    void failedDownloadLeavesNoFiles() throws IOException {
        server.respondWith(404);

        assertThrows(IOException.class, () -> cache.download(server.url("/cv/1.jpg"), "vn", "v1"));

        Path folder = tempDir.resolve("images/vn");
        try (var files = Files.list(folder)) {
            assertEquals(0, files.count(), "Ni la imagen ni el temporal .part deben quedar");
        }
    }

    @Test
    void isCachedHandlesNullAndMissingFiles() {
        assertFalse(cache.isCached(null));
        assertFalse(cache.isCached("images/vn/no-existe.jpg"));
    }

    @Test
    void extensionComesFromUrl() {
        assertEquals(".jpg", ImageCacheService.extensionOf("https://t.vndb.org/cv/95/75895.jpg"));
        assertEquals(".png", ImageCacheService.extensionOf("https://t.vndb.org/ch/1/2.PNG"));
        assertEquals(".jpg", ImageCacheService.extensionOf("https://t.vndb.org/cv/95/sin-extension"));
    }
}

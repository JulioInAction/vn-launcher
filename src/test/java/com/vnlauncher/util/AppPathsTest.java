package com.vnlauncher.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void createDirectoriesCreatesRootAndImages() {
        AppPaths paths = new AppPaths(tempDir.resolve("data"));
        paths.createDirectories();

        assertTrue(Files.isDirectory(paths.root()));
        assertTrue(Files.isDirectory(paths.imagesDir()));
        assertEquals(tempDir.resolve("data").resolve("library.db"), paths.databaseFile());
    }

    @Test
    void systemPropertyOverridesDefaultLocation() {
        String previous = System.getProperty("vnlauncher.home");
        try {
            System.setProperty("vnlauncher.home", tempDir.toString());
            assertEquals(tempDir, AppPaths.fromEnvironment().root());
        } finally {
            // Restaurar para no afectar a otros tests
            if (previous == null) System.clearProperty("vnlauncher.home");
            else System.setProperty("vnlauncher.home", previous);
        }
    }
}

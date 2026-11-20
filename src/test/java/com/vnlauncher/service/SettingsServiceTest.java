package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void localeEmulatorPathCanBeSavedChangedAndCleared() throws Exception {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        SettingsService settings = new SettingsService(new SettingsRepository(db));

        assertTrue(settings.localeEmulatorPath().isEmpty());

        settings.setLocaleEmulatorPath(tempDir.resolve("LE/LEProc.exe"));
        assertEquals(tempDir.resolve("LE/LEProc.exe").toAbsolutePath(), settings.localeEmulatorPath().orElseThrow());

        settings.setLocaleEmulatorPath(tempDir.resolve("Otro/LEProc.exe")); // sobrescribir (UPSERT)
        assertEquals(tempDir.resolve("Otro/LEProc.exe").toAbsolutePath(), settings.localeEmulatorPath().orElseThrow());

        settings.setLocaleEmulatorPath(null);
        assertTrue(settings.localeEmulatorPath().isEmpty());
    }
}

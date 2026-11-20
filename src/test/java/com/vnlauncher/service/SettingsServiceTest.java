package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.Theme;
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

    @Test
    void themeDefaultsToDarkAndIsSavedUnderKeyTheme() throws Exception {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        SettingsRepository repo = new SettingsRepository(db);
        SettingsService settings = new SettingsService(repo);

        assertEquals(Theme.DARK, settings.theme(), "Sin valor guardado: oscuro");

        settings.setTheme(Theme.LIGHT);
        assertEquals(Theme.LIGHT, settings.theme());
        assertEquals("claro", repo.get("theme").orElseThrow(), "Clave y valor tal como pide DISEÑO.md");

        repo.put("theme", "valor-raro");
        assertEquals(Theme.DARK, settings.theme(), "Un valor desconocido no rompe nada");
    }
}

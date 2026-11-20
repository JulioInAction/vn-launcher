package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.BlurLevel;
import com.vnlauncher.model.Theme;
import com.vnlauncher.model.WindowBounds;
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
        SettingsService settings = new SettingsService(new SettingsRepository(db), ContentFilter.withDefaults());

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
        SettingsService settings = new SettingsService(repo, ContentFilter.withDefaults());

        assertEquals(Theme.DARK, settings.theme(), "Sin valor guardado: oscuro");

        settings.setTheme(Theme.LIGHT);
        assertEquals(Theme.LIGHT, settings.theme());
        assertEquals("claro", repo.get("theme").orElseThrow(), "Clave y valor tal como pide DISEÑO.md");

        repo.put("theme", "valor-raro");
        assertEquals(Theme.DARK, settings.theme(), "Un valor desconocido no rompe nada");
    }

    @Test
    void windowBoundsAreRememberedAndBadValuesIgnored() throws Exception {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        SettingsRepository repo = new SettingsRepository(db);
        SettingsService settings = new SettingsService(repo, ContentFilter.withDefaults());

        assertTrue(settings.windowBounds().isEmpty(), "La primera vez no hay nada guardado");

        settings.setWindowBounds(new WindowBounds(120.4, 52, 1680, 936.6, true));
        assertEquals("120;52;1680;937;true", repo.get("window").orElseThrow(), "Se guarda redondeado");
        assertEquals(new WindowBounds(120, 52, 1680, 937, true), settings.windowBounds().orElseThrow());

        repo.put("window", "estropeado");
        assertTrue(settings.windowBounds().isEmpty(), "Un valor que no se entiende cuenta como 'nada guardado'");
    }

    @Test
    void systemThemeIsAThirdValue() throws Exception {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        SettingsRepository repo = new SettingsRepository(db);
        SettingsService settings = new SettingsService(repo, ContentFilter.withDefaults());

        settings.setTheme(Theme.SYSTEM);

        assertEquals(Theme.SYSTEM, settings.theme());
        assertEquals("sistema", repo.get("theme").orElseThrow());
    }

    @Test
    void contentSettingsHaveDefaultsAndUpdateTheFilter() throws Exception {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        ContentFilter filter = ContentFilter.withDefaults();
        SettingsService settings = new SettingsService(new SettingsRepository(db), filter);

        assertFalse(settings.showSpoilers(), "Por defecto los spoilers se ocultan");
        assertEquals(BlurLevel.EXPLICIT, settings.blurLevel(), "Por defecto, solo explícitas");

        settings.setShowSpoilers(true);
        settings.setBlurLevel(BlurLevel.NONE);

        assertTrue(settings.showSpoilers());
        assertEquals(BlurLevel.NONE, settings.blurLevel());
        assertEquals(BlurLevel.NONE, filter.level(), "El filtro de la app cambia al momento");
    }
}

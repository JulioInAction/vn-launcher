package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.vnlauncher.service.RestoreServiceTest.theme;
import static org.junit.jupiter.api.Assertions.*;

class PendingOperationsTest {

    private static final PendingOperation RESTORE =
            new PendingOperation(PendingOperation.Kind.RESTORE, LocalDateTime.of(2026, 11, 14, 19, 5));

    @TempDir
    Path tempDir;

    private AppPaths paths;
    private SettingsRepository current;

    @BeforeEach
    void setUp() throws Exception {
        paths = new AppPaths(tempDir);
        Database db = new Database(paths.databaseFile());
        db.migrate();
        current = new SettingsRepository(db);
        current.put("theme", "claro");
    }

    /** Deja preparada en pendiente/ una base con el tema oscuro y la nota de la operación. */
    private SettingsRepository stageDarkTheme(PendingOperation operation) throws Exception {
        Files.createDirectories(paths.pendingDir());
        Database staged = new Database(paths.stagedDatabaseFile());
        staged.migrate();
        SettingsRepository settings = new SettingsRepository(staged);
        settings.put("theme", "oscuro");
        operation.write(paths.pendingOperationFile());
        return settings;
    }

    private List<String> filesInDataDir() throws Exception {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.map(f -> f.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void theNewDatabaseTakesThePlaceOfTheOldOneOnlyOnce() throws Exception {
        stageDarkTheme(RESTORE);
        Files.writeString(Path.of(paths.databaseFile() + "-wal"), "cambios de la base vieja");

        assertEquals(new PendingOperations.Outcome.Done(RESTORE), PendingOperations.applyPending(paths).orElseThrow());

        assertEquals("oscuro", theme(paths.databaseFile()));
        assertEquals(List.of("library.db"), filesInDataDir(),
                "Ni la nota, ni pendiente/, ni la base de antes, ni su -wal");
        assertTrue(PendingOperations.applyPending(paths).isEmpty(), "Solo una vez");
    }

    @Test
    void ifTheNewDatabaseCannotBeUsedTheOldOneStays() throws Exception {
        stageDarkTheme(RESTORE);
        Files.writeString(paths.stagedDatabaseFile(), "se ha estropeado por el camino");

        PendingOperations.Outcome outcome = PendingOperations.applyPending(paths).orElseThrow();

        assertInstanceOf(PendingOperations.Outcome.Failed.class, outcome);
        assertEquals("claro", theme(paths.databaseFile()), "La de antes, en su sitio");
        assertEquals(List.of("library.db"), filesInDataDir(), "No se reintenta en cada arranque");
    }

    @Test
    void anUnreadableNoteFailsWithoutTouchingAnything() throws Exception {
        Files.writeString(paths.pendingOperationFile(), "operacion=otra-cosa\n");

        PendingOperations.Outcome outcome = PendingOperations.applyPending(paths).orElseThrow();

        assertNull(((PendingOperations.Outcome.Failed) outcome).operation());
        assertEquals("claro", theme(paths.databaseFile()));
        assertFalse(Files.exists(paths.pendingOperationFile()));
    }

    @Test
    void aSwapCutHalfwayIsUndoneAtStartup() throws Exception {
        // Como si se hubiera ido la luz justo después de apartar la base
        Files.move(paths.databaseFile(), Path.of(paths.databaseFile() + ".anterior"));

        assertTrue(PendingOperations.applyPending(paths).isEmpty());

        assertEquals("claro", theme(paths.databaseFile()), "Mejor la de antes que una vacía");
    }

    @Test
    void leftoversOfAProfileThatWasOnlyOpenedAreCleanedUp() throws Exception {
        Files.createDirectories(paths.stagedCoversDir());
        Files.writeString(paths.stagedDatabaseFile(), "perfil abierto y no importado");

        assertTrue(PendingOperations.applyPending(paths).isEmpty());

        assertFalse(Files.exists(paths.pendingDir()));
    }

    @Test
    void theWindowAndAWorkingLocaleEmulatorAreThisComputers() throws Exception {
        Path localLe = Files.writeString(tempDir.resolve("LEProc.exe"), "");
        current.put(SettingsService.WINDOW, "10;20;1300;800;true");
        current.put(SettingsService.LOCALE_EMULATOR_PATH, localLe.toString());
        SettingsRepository incoming = stageDarkTheme(RESTORE);
        incoming.put(SettingsService.WINDOW, "-1920;0;2560;1440;false");
        incoming.put(SettingsService.LOCALE_EMULATOR_PATH, "D:\\Herramientas\\LEProc.exe");

        PendingOperations.applyPending(paths).orElseThrow();

        SettingsRepository after = new SettingsRepository(new Database(paths.databaseFile()));
        assertEquals(Optional.of("10;20;1300;800;true"), after.get(SettingsService.WINDOW), "La ventana, siempre la de aquí");
        assertEquals(Optional.of(localLe.toString()), after.get(SettingsService.LOCALE_EMULATOR_PATH),
                "La de los datos nuevos no existe aquí y la de aquí sí");
        assertEquals(Optional.of("oscuro"), after.get("theme"), "El resto, de los datos nuevos");
    }

    @Test
    void withoutAWindowHereTheNewOneIsNotUsedEither() throws Exception {
        stageDarkTheme(RESTORE).put(SettingsService.WINDOW, "-1920;0;2560;1440;false");

        PendingOperations.applyPending(paths).orElseThrow();

        assertTrue(new SettingsRepository(new Database(paths.databaseFile())).get(SettingsService.WINDOW).isEmpty(),
                "Grande y centrada, como la primera vez");
    }

    @Test
    void theLocaleEmulatorOfTheNewDataWinsIfItExistsHere() {
        Path incoming = Path.of("C:/LE/LEProc.exe");
        Path local = Path.of("D:/LE/LEProc.exe");
        Set<Path> existing = Set.of(incoming, local);

        assertEquals(Optional.of(incoming), PendingOperations.chooseLocaleEmulator(
                Optional.of(incoming), Optional.of(local), existing::contains));
        assertEquals(Optional.of(local), PendingOperations.chooseLocaleEmulator(
                Optional.of(Path.of("Z:/no/esta.exe")), Optional.of(local), existing::contains));
        assertEquals(Optional.of(local), PendingOperations.chooseLocaleEmulator(
                Optional.empty(), Optional.of(local), existing::contains), "Los datos nuevos no tenían");
        Path missing = Path.of("Z:/no/esta.exe");
        assertEquals(Optional.of(missing), PendingOperations.chooseLocaleEmulator(
                Optional.of(missing), Optional.of(Path.of("Y:/tampoco.exe")), existing::contains),
                "Si no existe ninguna, la de los datos nuevos (Ajustes dirá «No encontrado»)");
    }
}

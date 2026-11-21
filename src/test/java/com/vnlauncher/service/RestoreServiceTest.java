package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.BackupInfo;
import com.vnlauncher.model.BackupKind;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RestoreServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 42, 3);

    @TempDir
    Path tempDir;

    private AppPaths paths;
    private Database db;
    private BackupService backups;
    private RestoreService restore;

    @BeforeEach
    void setUp() throws Exception {
        paths = new AppPaths(tempDir);
        db = new Database(paths.databaseFile());
        db.migrate();
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T10:42:03Z"), ZoneOffset.UTC);
        backups = new BackupService(db, paths.backupsDir(), clock, "1.0.0");
        restore = new RestoreService(paths, backups);
    }

    private String theme(Path file) throws Exception {
        return new SettingsRepository(new Database(file)).get("theme").orElse("");
    }

    /** Una copia hecha "hace días": con el tema oscuro (ahora es claro). */
    private BackupInfo oldBackupWithDarkTheme() throws Exception {
        new SettingsRepository(db).put("theme", "oscuro");
        Path file = paths.backupsDir().resolve("vn-launcher-2026-11-14_190500-semanal.db");
        Files.createDirectories(paths.backupsDir());
        db.backupTo(file);
        new SettingsRepository(db).put("theme", "claro");
        return BackupService.parse(file).orElseThrow();
    }

    @Test
    void preparingSetsTheCopyAsideMakesASafetyCopyAndLeavesANote() throws Exception {
        BackupInfo chosen = oldBackupWithDarkTheme();

        restore.prepare(chosen);

        assertEquals("oscuro", theme(paths.stagedDatabaseFile()), "La elegida, apartada para el arranque");
        assertEquals("claro", theme(paths.databaseFile()), "La base de ahora aún no se toca");
        BackupInfo safety = backups.list().getFirst();
        assertEquals(BackupKind.BEFORE_RESTORE, safety.kind());
        assertEquals("claro", theme(safety.file()), "La copia de seguridad es de cómo está ahora");
        assertEquals(new PendingOperation(PendingOperation.Kind.RESTORE, LocalDateTime.of(2026, 11, 14, 19, 5)),
                PendingOperation.read(paths.pendingOperationFile()));
    }

    @Test
    void theChosenCopySurvivesEvenIfTheSafetyCopyPushesItOut() throws Exception {
        BackupInfo oldest = oldBackupWithDarkTheme();
        // Otras 4 más nuevas: con la de «Antes de restaurar» serían 6 y se borra la más vieja (la elegida)
        for (int day = 15; day <= 18; day++) {
            db.backupTo(paths.backupsDir().resolve("vn-launcher-2026-11-" + day + "_120000-semanal.db"));
        }

        restore.prepare(oldest);

        assertFalse(Files.exists(oldest.file()), "La copia elegida ya no está en la lista...");
        assertEquals("oscuro", theme(paths.stagedDatabaseFile()), "...pero se apartó antes");
    }

    @Test
    void aCopyFromANewerVersionOrDamagedIsNotRestored() throws Exception {
        BackupInfo newer = oldBackupWithDarkTheme();
        try (Connection conn = new Database(newer.file()).getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = " + (Database.latestVersion() + 1));
        }
        InvalidBackupException tooNew = assertThrows(InvalidBackupException.class, () -> restore.prepare(newer));
        assertEquals(RestoreService.TOO_NEW, tooNew.getMessage());

        Path broken = Files.writeString(paths.backupsDir().resolve("vn-launcher-2026-11-20_120000-semanal.db"),
                "esto no es una base de datos");
        InvalidBackupException damaged = assertThrows(InvalidBackupException.class,
                () -> restore.check(broken));
        assertEquals(RestoreService.DAMAGED, damaged.getMessage());

        assertFalse(Files.exists(paths.stagedDatabaseFile()));
        assertFalse(Files.exists(paths.pendingOperationFile()), "No queda nada pendiente");
        assertEquals(2, backups.list().size(), "Ni se ha hecho la copia de seguridad");
    }

    @Test
    void checkingACopyLeavesItUntouched() throws Exception {
        BackupInfo chosen = oldBackupWithDarkTheme();
        byte[] before = Files.readAllBytes(chosen.file());

        restore.check(chosen.file());

        assertArrayEquals(before, Files.readAllBytes(chosen.file()));
        try (Stream<Path> files = Files.list(paths.backupsDir())) {
            assertEquals(List.of(chosen.file()), files.toList(), "Sin -wal ni -shm al lado");
        }
    }

    @Test
    void onTheNextStartTheCopyTakesThePlaceOfTheDatabase() throws Exception {
        restore.prepare(oldBackupWithDarkTheme());
        Files.writeString(Path.of(paths.databaseFile() + "-wal"), "cambios de la base vieja");

        RestoreService.Outcome outcome = RestoreService.applyPending(paths).orElseThrow();

        assertEquals(new RestoreService.Outcome.Done(
                new PendingOperation(PendingOperation.Kind.RESTORE, LocalDateTime.of(2026, 11, 14, 19, 5))), outcome);
        assertEquals("oscuro", theme(paths.databaseFile()));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(List.of("backups", "library.db"),
                    files.map(f -> f.getFileName().toString()).sorted().toList(),
                    "Ni la nota, ni la base apartada, ni la de antes, ni su -wal");
        }
        assertTrue(RestoreService.applyPending(paths).isEmpty(), "Solo una vez");
    }

    @Test
    void ifTheCopyCannotBeUsedTheDatabaseStaysAsItWas() throws Exception {
        restore.prepare(oldBackupWithDarkTheme());
        Files.writeString(paths.stagedDatabaseFile(), "se ha estropeado por el camino");

        RestoreService.Outcome outcome = RestoreService.applyPending(paths).orElseThrow();

        assertInstanceOf(RestoreService.Outcome.Failed.class, outcome);
        assertEquals("claro", theme(paths.databaseFile()), "La de antes, en su sitio");
        assertFalse(Files.exists(paths.pendingOperationFile()), "No se reintenta en cada arranque");
        assertFalse(Files.exists(paths.stagedDatabaseFile()));
        assertFalse(Files.exists(Path.of(paths.databaseFile() + ".anterior")));
    }

    @Test
    void anUnreadableNoteFailsWithoutTouchingAnything() throws Exception {
        Files.writeString(paths.pendingOperationFile(), "operacion=otra-cosa\n");

        RestoreService.Outcome outcome = RestoreService.applyPending(paths).orElseThrow();

        assertNull(((RestoreService.Outcome.Failed) outcome).operation());
        assertEquals(Database.latestVersion(), db.schemaVersion());
        assertFalse(Files.exists(paths.pendingOperationFile()));
    }

    @Test
    void aSwapCutHalfwayIsUndoneAtStartup() throws Exception {
        new SettingsRepository(db).put("theme", "claro");
        // Como si se hubiera ido la luz justo después de apartar la base
        Files.move(paths.databaseFile(), Path.of(paths.databaseFile() + ".anterior"));

        assertTrue(RestoreService.applyPending(paths).isEmpty());

        assertEquals("claro", theme(paths.databaseFile()), "Mejor la de antes que una vacía");
    }
}

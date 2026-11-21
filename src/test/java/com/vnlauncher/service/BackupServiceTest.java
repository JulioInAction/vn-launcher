package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.BackupInfo;
import com.vnlauncher.model.BackupKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    private static final Instant START = Instant.parse("2026-11-21T10:42:03Z");

    @TempDir
    Path tempDir;

    private Database db;
    private Path backups;
    private MutableClock clock;
    private BackupService service;

    /** Un reloj que el test adelanta a mano (para probar la copia semanal sin esperar una semana). */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        db = new Database(tempDir.resolve("library.db"));
        db.migrate();
        backups = tempDir.resolve("backups");
        clock = new MutableClock(START);
        service = new BackupService(db, backups, clock, "1.0.0");
    }

    @Test
    void theCopyHasWhatIsStillInTheWalAndANameThatSaysEverything() throws Exception {
        // Recién escrito: con WAL, esto aún no está en library.db, sino en library.db-wal
        new SettingsRepository(db).put("theme", "claro");

        BackupInfo backup = service.backup(BackupKind.WEEKLY);

        assertEquals("vn-launcher-2026-11-21_104203-semanal.db", backup.file().getFileName().toString());
        assertEquals(LocalDateTime.of(2026, 11, 21, 10, 42, 3), backup.createdAt());
        assertEquals("Semanal", backup.description());
        assertEquals(Files.size(backup.file()), backup.sizeBytes());
        Database copy = new Database(backup.file());
        assertEquals("claro", new SettingsRepository(copy).get("theme").orElseThrow(), "Copia consistente");
        assertEquals(Database.latestVersion(), copy.schemaVersion());
        try (var files = Files.list(backups)) {
            assertTrue(files.noneMatch(f -> f.toString().endsWith(".part")), "Sin restos de la copia a medias");
        }
    }

    @Test
    void beforeAnUpdateTheNameCarriesTheVersion() throws Exception {
        BackupInfo backup = service.backup(BackupKind.BEFORE_UPDATE);

        assertEquals("vn-launcher-2026-11-21_104203-actualizacion-1.0.0.db", backup.file().getFileName().toString());
        assertEquals("Antes de actualizar a 1.0.0", backup.description());
        assertEquals(backup, service.list().getFirst(), "Se lee igual del nombre");
    }

    @Test
    void onlyTheFiveNewestAreKeptAndOtherFilesAreNotTouched() throws Exception {
        Files.createDirectories(backups);
        Path notes = Files.writeString(backups.resolve("notas.txt"), "mías");
        Path other = Files.writeString(backups.resolve("vn-launcher-copia-a-mano.db"), "mía");

        for (int i = 0; i < 7; i++) {
            service.backup(i % 2 == 0 ? BackupKind.WEEKLY : BackupKind.BEFORE_IMPORT);
            clock.advance(Duration.ofDays(1));
        }

        List<BackupInfo> left = service.list();
        assertEquals(BackupService.KEEP, left.size());
        assertEquals(LocalDateTime.of(2026, 11, 27, 10, 42, 3), left.getFirst().createdAt(), "La más nueva, primero");
        assertEquals(LocalDateTime.of(2026, 11, 23, 10, 42, 3), left.getLast().createdAt(), "Las dos más viejas, fuera");
        assertTrue(Files.exists(notes) && Files.exists(other), "Lo que no es una copia nuestra no se toca");
    }

    @Test
    void theWeeklyCopyIsMadeWhenTheLastOneIsOlderThanAWeek() throws Exception {
        assertTrue(service.weeklyIfDue(true).isPresent(), "Sin ninguna: toca");

        clock.advance(Duration.ofDays(3));
        assertTrue(service.weeklyIfDue(true).isEmpty(), "Hace 3 días: no");
        service.backup(BackupKind.BEFORE_UPDATE);
        clock.advance(Duration.ofDays(5));
        assertTrue(service.weeklyIfDue(false).isEmpty(), "Desactivada: nunca");
        assertTrue(service.weeklyIfDue(true).isPresent(),
                "Hace 8 días de la última semanal (la de actualizar no cuenta)");

        assertEquals(2, service.list().stream().filter(b -> b.kind() == BackupKind.WEEKLY).count());
    }

    @Test
    void beforeMigrationOnlyWhenAnExistingDatabaseIsBehind() throws Exception {
        assertTrue(service.beforeMigration().isEmpty(), "Al día: nada que proteger");

        Path fresh = tempDir.resolve("nueva.db");
        BackupService forFresh = new BackupService(new Database(fresh), backups, clock, "1.0.0");
        assertTrue(forFresh.beforeMigration().isEmpty(), "Primera vez: no hay base");
        assertFalse(Files.exists(fresh), "Comprobarlo no crea el archivo");

        Database old = new Database(tempDir.resolve("vieja.db"));
        old.migrateTo(3);
        BackupInfo backup = new BackupService(old, backups, clock, "1.0.0").beforeMigration().orElseThrow();
        assertEquals(BackupKind.BEFORE_UPDATE, backup.kind());
        assertEquals(3, new Database(backup.file()).schemaVersion(), "La copia es la base de antes de migrar");
    }

    @Test
    void namesThatAreNotOursAreIgnored() {
        assertTrue(BackupService.parse(Path.of("vn-launcher-2026-11-21_104203-semanal.db.part")).isEmpty());
        assertTrue(BackupService.parse(Path.of("vn-launcher-2026-11-21_104203-otra-cosa.db")).isEmpty());
        assertTrue(BackupService.parse(Path.of("vn-launcher-2026-13-45_999999-semanal.db")).isEmpty());
        assertTrue(BackupService.parse(Path.of("library.db")).isEmpty());
    }
}

package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.BackupKind;
import com.vnlauncher.model.ProfileManifest;
import com.vnlauncher.model.ProfilePreview;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dos "ordenadores" (dos carpetas de datos): se exporta en uno y se importa en el otro.
 */
class ProfileServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T10:42:03Z"), ZoneOffset.UTC);
    private static final String MANIFEST_V1 = """
            {"format": 1, "app": "VN Launcher", "appVersion": "0.9.0", "schemaVersion": %d,
             "exportedAt": "2026-11-14T19:05:00", "novels": 1, "routes": 0, "playtimeSeconds": 0, "covers": 0,
             "algoDeUnaVersionFutura": true}""";

    @TempDir
    Path tempDir;

    private Computer home;
    private Computer laptop;

    /** Una carpeta de datos con su base y sus servicios, como en un ordenador. */
    private static final class Computer {
        final AppPaths paths;
        final Database db;
        final BackupService backups;
        final ProfileService profiles;

        Computer(Path root, long maxBytes) throws Exception {
            paths = new AppPaths(root);
            paths.createDirectories();
            db = new Database(paths.databaseFile());
            db.migrate();
            backups = new BackupService(db, paths.backupsDir(), CLOCK, "1.0.0");
            profiles = new ProfileService(db, paths, backups, CLOCK, "1.0.0", maxBytes);
        }

        void sql(String... statements) throws Exception {
            try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
                for (String statement : statements) {
                    st.execute(statement);
                }
            }
        }

        long count(String table) throws Exception {
            try (Connection conn = new Database(paths.databaseFile()).getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                return rs.getLong(1);
            }
        }

        /** Abre, confirma y reinicia: lo que pasa al pulsar «Importar perfil». */
        PendingOperations.Outcome importAndRestart(Path zip) throws Exception {
            profiles.prepareImport(profiles.open(zip));
            return PendingOperations.applyPending(paths).orElseThrow();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        home = new Computer(tempDir.resolve("casa"), ProfileService.MAX_BYTES);
        laptop = new Computer(tempDir.resolve("portatil"), ProfileService.MAX_BYTES);
        home.sql("INSERT INTO vn (id, title, fetched_at) VALUES ('v4', 'CLANNAD', 0), ('v11', 'Fate/stay night', 0)",
                "INSERT INTO library_entry (vn_id, added_at) VALUES ('v4', 0), ('v11', 0)",
                "INSERT INTO route (vn_id, name) VALUES ('v4', 'Nagisa'), ('v4', 'Kyou'), ('v11', 'Saber')",
                "INSERT INTO play_session (vn_id, started_at, ended_at, duration_seconds) "
                        + "VALUES ('v4', 0, 7200, 7200), ('v11', 0, 5400, 5400)");
        laptop.sql("INSERT INTO vn (id, title, fetched_at) VALUES ('v17', 'Ever17', 0)",
                "INSERT INTO library_entry (vn_id, added_at) VALUES ('v17', 0)");
    }

    @Test
    void theManifestSaysWhatTheProfileBrings() throws Exception {
        Files.writeString(home.paths.coversDir().resolve("v4-12345.jpg"), "portada");
        Files.writeString(home.paths.coversDir().resolve("notas mias.txt"), "no es una portada");

        ProfileManifest manifest = home.profiles.export(tempDir.resolve("perfil.zip"));

        assertEquals(new ProfileManifest(1, "VN Launcher", "1.0.0", Database.latestVersion(),
                "2026-11-21T10:42:03", 2, 3, 12_600, 1), manifest);
        assertEquals("vn-launcher-perfil-2026-11-21.zip", home.profiles.suggestedFileName());
        assertFalse(Files.exists(home.paths.root().resolve("exportar.db")), "Sin restos de la exportación");
    }

    @Test
    void exportedInOneComputerAndImportedInAnother() throws Exception {
        Path zip = tempDir.resolve("perfil.zip");
        Files.writeString(home.paths.coversDir().resolve("v4-12345.jpg"), "portada de casa");
        Files.writeString(home.paths.coversDir().resolve("v11-propia-1795186093000.png"), "otra de casa");
        Files.writeString(laptop.paths.coversDir().resolve("v4-12345.jpg"), "la misma, ya estaba");
        Files.writeString(laptop.paths.coversDir().resolve("v17-propia-1.png"), "solo del portátil");
        home.profiles.export(zip);

        ProfilePreview preview = laptop.profiles.open(zip);
        assertEquals("perfil.zip", preview.fileName());
        assertEquals(LocalDateTime.of(2026, 11, 21, 10, 42, 3), preview.exportedAt());
        assertEquals(1, preview.currentNovels(), "Las del portátil, para el aviso");
        assertEquals(1, laptop.count("library_entry"), "Abrirlo no cambia nada");

        laptop.profiles.prepareImport(preview);
        assertEquals(BackupKind.BEFORE_IMPORT, laptop.backups.list().getFirst().kind());
        assertInstanceOf(PendingOperations.Outcome.Done.class, PendingOperations.applyPending(laptop.paths).orElseThrow());

        assertEquals(2, laptop.count("library_entry"));
        assertEquals(3, laptop.count("route"));
        assertEquals(2, laptop.count("play_session"));
        Path covers = laptop.paths.coversDir();
        assertEquals("la misma, ya estaba", Files.readString(covers.resolve("v4-12345.jpg")));
        assertEquals("otra de casa", Files.readString(covers.resolve("v11-propia-1795186093000.png")));
        assertEquals("solo del portátil", Files.readString(covers.resolve("v17-propia-1.png")), "Nunca se borran");
        assertFalse(Files.exists(laptop.paths.pendingDir()));
    }

    @Test
    void anOldProfileIsBroughtUpToDate() throws Exception {
        Path old = tempDir.resolve("vieja.db");
        Database oldDb = new Database(old);
        oldDb.migrateTo(3);
        try (Connection conn = oldDb.getConnection(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO vn (id, title, fetched_at) VALUES ('v4', 'CLANNAD', 0)");
            st.execute("INSERT INTO library_entry (vn_id, added_at) VALUES ('v4', 0)");
        }
        Path zip = zip(tempDir.resolve("antiguo.zip"), Map.of(
                "manifest.json", String.format(MANIFEST_V1, 3).getBytes(StandardCharsets.UTF_8),
                "library.db", Files.readAllBytes(old)));

        laptop.importAndRestart(zip);
        Database migrated = new Database(laptop.paths.databaseFile());
        migrated.migrate(); // lo que hace la app al arrancar, justo después

        assertEquals(Database.latestVersion(), migrated.schemaVersion());
        assertEquals(1, laptop.count("library_entry"));
    }

    @Test
    void aProfileFromANewerVersionIsNotImported() throws Exception {
        Path zip = zip(tempDir.resolve("nuevo.zip"), Map.of(
                "manifest.json", String.format(MANIFEST_V1, Database.latestVersion() + 1).getBytes(StandardCharsets.UTF_8),
                "library.db", Files.readAllBytes(home.paths.databaseFile())));

        assertRejected(ProfileService.TOO_NEW, zip);
    }

    @Test
    void whatIsNotAProfileIsNotImported() throws Exception {
        assertRejected(ProfileService.NOT_A_PROFILE,
                Files.writeString(tempDir.resolve("foto.zip"), "esto no es un zip"));
        assertRejected(ProfileService.NOT_A_PROFILE, zip(tempDir.resolve("sin-manifest.zip"),
                Map.of("library.db", Files.readAllBytes(home.paths.databaseFile()))));
        assertRejected(ProfileService.DAMAGED, zip(tempDir.resolve("roto.zip"), Map.of(
                "manifest.json", String.format(MANIFEST_V1, 5).getBytes(StandardCharsets.UTF_8),
                "library.db", "esto no es una base de datos".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void dangerousNamesAreRejectedWithoutWritingAnything() throws Exception {
        for (String name : new String[]{"../fuera.txt", "covers/../../fuera.txt", "/etc/fuera.txt",
                "covers/C:\\fuera.png", "covers/portada.png:oculto", "covers/sub/portada.png"}) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("manifest.json", String.format(MANIFEST_V1, 5).getBytes(StandardCharsets.UTF_8));
            entries.put(name, "malo".getBytes(StandardCharsets.UTF_8));
            assertRejected(ProfileService.UNSAFE, zip(tempDir.resolve("peligroso.zip"), entries));
        }
        assertFalse(Files.exists(tempDir.resolve("fuera.txt")));
        assertFalse(Files.exists(laptop.paths.root().resolve("fuera.txt")));
    }

    @Test
    void aZipThatInflatesTooMuchIsStopped() throws Exception {
        Computer small = new Computer(tempDir.resolve("pequeno"), 1024 * 1024);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", String.format(MANIFEST_V1, 5).getBytes(StandardCharsets.UTF_8));
        entries.put("library.db", new byte[2 * 1024 * 1024]); // ceros: se comprimen a casi nada

        InvalidDataFileException e = assertThrows(InvalidDataFileException.class,
                () -> small.profiles.open(zip(tempDir.resolve("bomba.zip"), entries)));
        assertEquals(String.format(ProfileService.TOO_BIG, ProfileService.MAX_GB), e.getMessage());
        assertFalse(Files.exists(small.paths.pendingDir()));
    }

    @Test
    void cancellingLeavesNothingBehind() throws Exception {
        Path zip = tempDir.resolve("perfil.zip");
        home.profiles.export(zip);
        laptop.profiles.open(zip);

        laptop.profiles.discardPreview();

        assertFalse(Files.exists(laptop.paths.pendingDir()));
        assertFalse(Files.exists(laptop.paths.pendingOperationFile()));
        assertEquals(1, laptop.count("library_entry"));
    }

    @Test
    void theLaptopKeepsItsWindowAndItsLocaleEmulator() throws Exception {
        Path laptopLe = Files.writeString(tempDir.resolve("LEProc.exe"), "");
        new SettingsRepository(laptop.db).put(SettingsService.LOCALE_EMULATOR_PATH, laptopLe.toString());
        new SettingsRepository(laptop.db).put(SettingsService.WINDOW, "0;0;1366;740;true");
        SettingsRepository homeSettings = new SettingsRepository(home.db);
        homeSettings.put(SettingsService.LOCALE_EMULATOR_PATH, "C:\\Programas\\LocaleEmulator\\LEProc.exe");
        homeSettings.put(SettingsService.WINDOW, "-1920;0;2560;1440;false");
        homeSettings.put("theme", "claro");
        Path zip = tempDir.resolve("perfil.zip");
        home.profiles.export(zip);

        laptop.importAndRestart(zip);

        SettingsRepository after = new SettingsRepository(new Database(laptop.paths.databaseFile()));
        assertEquals("0;0;1366;740;true", after.get(SettingsService.WINDOW).orElseThrow());
        assertEquals(laptopLe.toString(), after.get(SettingsService.LOCALE_EMULATOR_PATH).orElseThrow());
        assertEquals("claro", after.get("theme").orElseThrow(), "El resto de ajustes viene del perfil");
    }

    @Test
    void safeNames() {
        assertTrue(ProfileService.isSafeEntryName("covers/v4-12345.jpg"));
        assertTrue(ProfileService.isSafeEntryName("covers/"));
        assertFalse(ProfileService.isSafeEntryName(""));
        assertFalse(ProfileService.isSafeEntryName("covers/./x.png"));
        assertFalse(ProfileService.isSafeEntryName("covers/x\u0000.png"));
        assertFalse(ProfileService.isSafeEntryName("a\\..\\b"));
    }

    private void assertRejected(String message, Path zip) {
        InvalidDataFileException e = assertThrows(InvalidDataFileException.class, () -> laptop.profiles.open(zip));
        assertEquals(message, e.getMessage());
        assertFalse(Files.exists(laptop.paths.pendingDir()), "Lo descomprimido se borra");
    }

    private static Path zip(Path target, Map<String, byte[]> entries) throws Exception {
        Files.deleteIfExists(target);
        try (OutputStream file = Files.newOutputStream(target); ZipOutputStream out = new ZipOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return target;
    }
}

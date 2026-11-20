package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.PlaySessionRepository;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.VisualNovel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LaunchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T20:00:00Z");

    @TempDir
    Path tempDir;

    private LibraryRepository library;
    private SettingsService settings;
    private PlaytimeTracker tracker;
    private Path gameDir;
    private Path exe;

    /** Lo que el ProcessStarter falso ha recibido en cada llamada. */
    private record Call(List<String> command, Path dir) {
    }

    private final List<Call> calls = new ArrayList<>();
    /** ProcessStarter falso: apunta la llamada y no arranca nada (devuelve null como Process). */
    private final ProcessStarter recording = (command, dir) -> {
        calls.add(new Call(command, dir));
        return null;
    };

    @BeforeEach
    void setUp() throws Exception {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        new VnRepository(db).save(new VisualNovel("v4", "CLANNAD", null, null, null, null, null, null, null,
                List.of()), NOW);
        library = new LibraryRepository(db);
        library.add("v4", NOW);
        settings = new SettingsService(new SettingsRepository(db), ContentFilter.withDefaults());
        // Tracker real (con BD) pero con un escáner que nunca ve procesos: aquí no se prueba él
        tracker = new PlaytimeTracker(new PlaySessionRepository(db), dir -> false, () -> NOW);

        // Un "juego" falso en una carpeta con espacios (caso típico: "C:\Program Files (x86)\...")
        gameDir = Files.createDirectories(tempDir.resolve("Mis Juegos/CLANNAD"));
        exe = Files.createFile(gameDir.resolve("RealLive.exe"));
    }

    private LaunchService service(ProcessStarter starter) {
        return new LaunchService(library, settings, starter, tracker, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void launchesExeDirectlyFromItsFolder() throws Exception {
        LaunchService service = service(recording);
        service.configure("v4", exe, false);

        LaunchedGame game = service.launch("v4");

        assertEquals(List.of(new Call(List.of(exe.toAbsolutePath().toString()), gameDir.toAbsolutePath())), calls);
        assertEquals(gameDir.toAbsolutePath(), game.gameDir());
        assertFalse(game.elevated());
        assertEquals(NOW, library.findByVnId("v4").orElseThrow().lastPlayedAt(), "Se apunta la última partida");
    }

    @Test
    void launchesThroughLocaleEmulatorWithDefaultProfile() throws Exception {
        Path leProc = Files.createFile(tempDir.resolve("LEProc.exe"));
        settings.setLocaleEmulatorPath(leProc);
        LaunchService service = service(recording);
        service.configure("v4", exe, true);

        service.launch("v4");

        Call call = calls.getFirst();
        assertEquals(List.of(leProc.toAbsolutePath().toString(), "-run", exe.toAbsolutePath().toString()),
                call.command());
        assertEquals(gameDir.toAbsolutePath(), call.dir(), "También con LE, el directorio es el del juego");
    }

    @Test
    void launchStartsTrackingAndRefusesToLaunchTwice() throws Exception {
        LaunchService service = service(recording);
        service.configure("v4", exe, false);

        service.launch("v4");
        assertTrue(tracker.isRunning("v4"), "Al lanzar empieza a medirse el tiempo");

        LaunchException e = assertThrows(LaunchException.class, () -> service.launch("v4"));
        assertEquals(LaunchException.Reason.ALREADY_RUNNING, e.reason());
        assertEquals(1, calls.size(), "No se lanza una segunda copia");
    }

    @Test
    void configProgramRunsFromItsFolderWithLocaleEmulatorButIsNotAPlay() throws Exception {
        Path leProc = Files.createFile(tempDir.resolve("LEProc.exe"));
        settings.setLocaleEmulatorPath(leProc);
        Path toolsDir = Files.createDirectories(gameDir.resolve("tools"));
        Path config = Files.createFile(toolsDir.resolve("config.exe"));
        LaunchService service = service(recording);
        service.configure("v4", exe, true);
        service.setConfigExecutable("v4", config);

        service.launchConfig("v4", false);

        assertEquals(new Call(List.of(leProc.toAbsolutePath().toString(), "-run",
                config.toAbsolutePath().toString()), toolsDir.toAbsolutePath()), calls.getFirst());
        assertFalse(tracker.isRunning("v4"), "La configuración no cuenta como tiempo de juego");
        assertNull(library.findByVnId("v4").orElseThrow().lastPlayedAt(), "Ni como última partida");
    }

    @Test
    void configProgramCanBeRemovedAndThenFails() throws Exception {
        Path config = Files.createFile(gameDir.resolve("config.exe"));
        LaunchService service = service(recording);
        service.setConfigExecutable("v4", config);
        assertTrue(library.findByVnId("v4").orElseThrow().hasConfigExecutable());

        service.setConfigExecutable("v4", null);

        assertFalse(library.findByVnId("v4").orElseThrow().hasConfigExecutable());
        LaunchException e = assertThrows(LaunchException.class, () -> service.launchConfig("v4", false));
        assertEquals(LaunchException.Reason.NO_EXECUTABLE, e.reason());
    }

    @Test
    void missingConfigProgramCarriesItsPath() throws Exception {
        Path config = Files.createFile(gameDir.resolve("config.exe"));
        LaunchService service = service(recording);
        service.setConfigExecutable("v4", config);
        Files.delete(config);

        LaunchException e = assertThrows(LaunchException.class, () -> service.launchConfig("v4", false));

        assertEquals(LaunchException.Reason.EXECUTABLE_NOT_FOUND, e.reason());
        assertEquals(config.toAbsolutePath(), e.path());
        assertTrue(calls.isEmpty());
    }

    @Test
    void failsWithoutExecutable() {
        LaunchException e = assertThrows(LaunchException.class, () -> service(recording).launch("v4"));
        assertEquals(LaunchException.Reason.NO_EXECUTABLE, e.reason());
        assertTrue(calls.isEmpty());
    }

    @Test
    void failsIfExecutableWasMovedOrDeleted() throws Exception {
        LaunchService service = service(recording);
        service.configure("v4", exe, false);
        Files.delete(exe);

        LaunchException e = assertThrows(LaunchException.class, () -> service.launch("v4"));
        assertEquals(LaunchException.Reason.EXECUTABLE_NOT_FOUND, e.reason());
    }

    @Test
    void failsIfLocaleEmulatorIsNeededButNotConfiguredOrMissing() throws Exception {
        LaunchService service = service(recording);
        service.configure("v4", exe, true);

        LaunchException notConfigured = assertThrows(LaunchException.class, () -> service.launch("v4"));
        assertEquals(LaunchException.Reason.LOCALE_EMULATOR_NOT_CONFIGURED, notConfigured.reason());

        settings.setLocaleEmulatorPath(tempDir.resolve("no-existe/LEProc.exe"));
        LaunchException missing = assertThrows(LaunchException.class, () -> service.launch("v4"));
        assertEquals(LaunchException.Reason.LOCALE_EMULATOR_NOT_FOUND, missing.reason());
        assertTrue(calls.isEmpty());
        assertNull(library.findByVnId("v4").orElseThrow().lastPlayedAt(), "Si no se lanza, no cuenta como partida");
    }

    @Test
    void canLaunchOnceWithoutLocaleEmulator() throws Exception {
        LaunchService service = service(recording);
        service.configure("v4", exe, true); // usa LE, pero LEProc.exe no está configurado

        service.launch("v4", true);         // "Jugar sin Locale Emulator"

        assertEquals(List.of(exe.toAbsolutePath().toString()), calls.getFirst().command());
        assertTrue(library.findByVnId("v4").orElseThrow().useLocaleEmulator(), "La opción no se cambia");
    }

    @Test
    void missingExecutableErrorCarriesThePath() throws Exception {
        LaunchService service = service(recording);
        service.configure("v4", exe, false);
        Files.delete(exe);

        LaunchException e = assertThrows(LaunchException.class, () -> service.launch("v4"));
        assertEquals(exe.toAbsolutePath(), e.path().toAbsolutePath(), "El diálogo de error muestra la ruta");
    }

    @Test
    void retriesWithCmdStartWhenWindowsAsksForElevation() throws Exception {
        ProcessStarter needsAdmin = (command, dir) -> {
            calls.add(new Call(command, dir));
            if (calls.size() == 1) {
                throw new IOException("Cannot run program \"RealLive.exe\": CreateProcess error=740, "
                        + "The requested operation requires elevation");
            }
            return null;
        };
        LaunchService service = service(needsAdmin);
        service.configure("v4", exe, false);

        LaunchedGame game = service.launch("v4");

        assertTrue(game.elevated());
        assertEquals(List.of("cmd.exe", "/c", "start", "", "/D", gameDir.toAbsolutePath().toString(),
                exe.toAbsolutePath().toString()), calls.get(1).command());
    }

    @Test
    void otherStartErrorsAreReported() throws Exception {
        LaunchService service = service((command, dir) -> {
            throw new IOException("CreateProcess error=193, %1 is not a valid Win32 application");
        });
        service.configure("v4", exe, false);

        LaunchException e = assertThrows(LaunchException.class, () -> service.launch("v4"));
        assertEquals(LaunchException.Reason.START_FAILED, e.reason());
    }

    /**
     * Prueba con un proceso REAL: un script que escribe en un fichero el directorio desde el que
     * se ejecuta. Si LaunchService no fijara el directorio de trabajo, el fichero aparecería en
     * la carpeta del proyecto (desde donde corre Maven) y no en la del "juego".
     */
    @Test
    void realProcessRunsInsideTheGameFolder() throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path script;
        if (windows) {
            script = gameDir.resolve("game.bat");
            Files.writeString(script, "@cd > cwd.txt\r\n");
        } else {
            script = gameDir.resolve("game.sh");
            Files.writeString(script, "#!/bin/sh\npwd > cwd.txt\n");
            script.toFile().setExecutable(true);
        }
        LaunchService service = service(ProcessStarter.system());
        service.configure("v4", script, false);

        LaunchedGame game = service.launch("v4");
        assertTrue(game.process().waitFor(10, TimeUnit.SECONDS), "El script debería terminar enseguida");

        Path written = gameDir.resolve("cwd.txt");
        assertTrue(Files.exists(written), "cwd.txt debe crearse en la carpeta del juego");
        assertEquals(gameDir.toRealPath(), Path.of(Files.readString(written).strip()).toRealPath());
    }
}

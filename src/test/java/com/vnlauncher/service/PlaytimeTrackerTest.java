package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.PlaySessionRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.VisualNovel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del registro de tiempo con un reloj y un "escáner de procesos" falsos: nosotros decidimos
 * qué hora es y si el juego está abierto, y llamamos a tick() a mano. Así se simulan 10 minutos
 * de juego en milisegundos.
 */
class PlaytimeTrackerTest {

    private static final Instant T0 = Instant.parse("2026-11-20T20:00:00Z");

    @TempDir
    Path tempDir;

    private Database db;
    private PlaySessionRepository sessions;
    private PlaytimeTracker tracker;

    private Instant now = T0;
    private boolean gameRunning = false;
    private final List<String> events = new ArrayList<>();
    private Path gameDir;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        new VnRepository(db).save(new VisualNovel("v4", "CLANNAD", null, null, null, null, null, null, null,
                List.of()), T0);
        new LibraryRepository(db).add("v4", T0);
        sessions = new PlaySessionRepository(db);
        gameDir = tempDir.resolve("CLANNAD");

        // Escáner falso: "hay un proceso en la carpeta del juego" si gameRunning es true
        ProcessScanner scanner = dir -> gameRunning && dir.equals(gameDir);
        tracker = new PlaytimeTracker(sessions, scanner, () -> now);
        tracker.addListener(new PlaytimeTracker.Listener() {
            @Override
            public void sessionStarted(String vnId) {
                events.add("start " + vnId);
            }

            @Override
            public void sessionEnded(String vnId, Duration played) {
                events.add("end " + vnId + " " + played.toSeconds() + "s");
            }
        });
    }

    /** Lanza el "juego": el proceso que arrancamos es null (como LEProc, que se cierra enseguida). */
    private void launch() throws Exception {
        tracker.track(new LaunchedGame("v4", null, gameDir, now, false));
    }

    /** Avanza el reloj de 5 en 5 s (como el tracker real) hasta {@code secondsFromStart}. */
    private void runUntil(int secondsFromStart) throws Exception {
        while (now.isBefore(T0.plusSeconds(secondsFromStart))) {
            now = now.plus(PlaytimeTracker.POLL);
            tracker.tick();
        }
    }

    @Test
    void childProcessThatStartsLateIsTracked() throws Exception {
        launch();
        runUntil(10);           // 10 s sin ver nada: LEProc arrancando el juego (dentro del margen)
        gameRunning = true;
        runUntil(600);          // el juego (proceso hijo) abierto hasta el segundo 600
        assertTrue(tracker.isRunning("v4"));
        gameRunning = false;
        runUntil(640);          // cerrado: a los 15 s sin verlo se cierra la sesión

        assertFalse(tracker.isRunning("v4"));
        assertEquals(600, sessions.totalSeconds("v4"), "Desde el lanzamiento hasta la última vez que se vio");
        assertEquals(List.of("start v4", "end v4 600s"), events);
    }

    @Test
    void gameThatNeverStartsIsDiscarded() throws Exception {
        launch();
        runUntil(40);           // pasa el margen de 30 s sin ver el juego nunca

        assertFalse(tracker.isRunning("v4"));
        assertEquals(0, sessions.totalSeconds("v4"));
        assertEquals("end v4 0s", events.getLast());
        assertEquals(0, sessions.closeUnfinished(), "La sesión se borró, no quedó a medias");
    }

    @Test
    void shortGapDoesNotSplitTheSession() throws Exception {
        launch();
        gameRunning = true;
        runUntil(100);
        gameRunning = false;    // el juego se reinicia: 10 s sin verlo (menos que la tolerancia de 15)
        runUntil(110);
        gameRunning = true;
        runUntil(300);
        gameRunning = false;
        runUntil(330);

        assertEquals(300, sessions.totalSeconds("v4"));
        assertEquals(List.of("start v4", "end v4 300s"), events, "Una sola sesión");
    }

    @Test
    void sessionIsRecoveredFromHeartbeatAfterCrash() throws Exception {
        launch();
        gameRunning = true;
        runUntil(95);           // latidos guardados en 30, 60 y 90 s
        // ¡El launcher se cuelga aquí! No se llama a tick() ni a shutdown().
        // Al volver a arrancar, AppContext llama a closeUnfinished():
        assertEquals(1, sessions.closeUnfinished());
        assertEquals(90, sessions.totalSeconds("v4"), "Se pierden como mucho los segundos desde el último latido");
    }

    @Test
    void closingTheLauncherClosesOpenSessions() throws Exception {
        launch();
        gameRunning = true;
        runUntil(50);

        tracker.shutdown();

        assertFalse(tracker.isRunning("v4"));
        assertEquals(50, sessions.totalSeconds("v4"));
        assertEquals(0, sessions.closeUnfinished());
    }

    @Test
    void totalsAddUpSeveralSessions() throws Exception {
        launch();
        gameRunning = true;
        runUntil(100);
        gameRunning = false;
        runUntil(130);
        launch();               // segunda partida empieza en t=130
        gameRunning = true;
        runUntil(330);
        gameRunning = false;
        runUntil(360);

        assertEquals(100 + 200, sessions.totalSeconds("v4"));
        assertEquals(300L, sessions.totalSecondsByVn().get("v4"));
    }
}

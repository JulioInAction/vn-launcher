package com.vnlauncher.service;

import com.vnlauncher.db.repo.PlaySessionRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mide cuánto tiempo está abierto cada juego lanzado y lo guarda como sesiones (play_session).
 *
 * <p><b>Cómo sabe si un juego sigue abierto.</b> Cada {@link #POLL} pregunta al
 * {@link ProcessScanner} si hay algún proceso ejecutándose DESDE LA CARPETA DEL JUEGO. Así da
 * igual que el juego lance un proceso hijo y cierre el original, o que pase por Locale Emulator.
 * Además cuenta como "abierto" el proceso que arrancamos nosotros mientras siga vivo.
 *
 * <p><b>Márgenes</b> (evitan cortar sesiones por error):
 * <ul>
 *   <li>{@link #STARTUP_GRACE}: durante los primeros segundos la sesión no se cierra aunque no
 *       se vea nada (LEProc o el lanzador del juego tardan en arrancar el ejecutable real).</li>
 *   <li>{@link #GAP_TOLERANCE}: si el juego desaparece un momento (p. ej. se reinicia a sí mismo),
 *       la sesión solo se cierra si lleva este tiempo sin verse.</li>
 * </ul>
 * La sesión dura desde el lanzamiento hasta la última vez que se vio el juego.
 *
 * <p><b>Hilos.</b> {@link #tick()} se ejecuta en un hilo propio cada 5 s; los listeners se
 * llaman desde ese hilo, así que la interfaz tiene que usar Platform.runLater.
 */
public class PlaytimeTracker {

    private static final System.Logger LOG = System.getLogger(PlaytimeTracker.class.getName());

    static final Duration POLL = Duration.ofSeconds(5);
    static final Duration STARTUP_GRACE = Duration.ofSeconds(30);
    static final Duration GAP_TOLERANCE = Duration.ofSeconds(15);
    /** Cada cuánto se guarda el "latido" en la BD (no hace falta escribir cada 5 s). */
    static final Duration HEARTBEAT_EVERY = Duration.ofSeconds(30);

    /** Avisos para la interfaz. */
    public interface Listener {
        void sessionStarted(String vnId);

        /** @param played tiempo de la sesión (cero si el juego no llegó a arrancar) */
        void sessionEnded(String vnId, Duration played);
    }

    /** Estado de un juego que estamos vigilando. Solo lo modifica tick() (synchronized). */
    private static final class Tracked {
        final long sessionId;
        final String vnId;
        final Path gameDir;
        final Process process;   // puede ser null (tests) o no ser el juego en sí (LEProc, cmd)
        final Instant startedAt;
        Instant lastSeen;        // null = todavía no se ha visto el juego abierto
        Instant lastHeartbeat;

        Tracked(long sessionId, LaunchedGame game) {
            this.sessionId = sessionId;
            this.vnId = game.vnId();
            this.gameDir = game.gameDir();
            this.process = game.process();
            this.startedAt = game.startedAt();
            this.lastHeartbeat = game.startedAt();
        }
    }

    private final PlaySessionRepository sessions;
    private final ProcessScanner scanner;
    private final InstantSource clock;
    // Colecciones "concurrentes": se usan desde varios hilos (tick, interfaz, tareas de fondo)
    private final Map<String, Tracked> active = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    public PlaytimeTracker(PlaySessionRepository sessions, ProcessScanner scanner, InstantSource clock) {
        this.sessions = sessions;
        this.scanner = scanner;
        this.clock = clock;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** ¿Se está jugando ahora mismo a esta VN? */
    public boolean isRunning(String vnId) {
        return active.containsKey(vnId);
    }

    /** ¿Hay algún juego abierto? (Restaurar una copia espera a que se cierren.) */
    public boolean isAnyRunning() {
        return !active.isEmpty();
    }

    /** Empieza a vigilar un juego recién lanzado: crea su sesión "en curso". */
    public synchronized void track(LaunchedGame game) throws SQLException {
        long id = sessions.start(game.vnId(), game.startedAt());
        active.put(game.vnId(), new Tracked(id, game));
        listeners.forEach(l -> l.sessionStarted(game.vnId()));
    }

    /** Arranca la comprobación periódica en un hilo propio. */
    public void startPolling() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "playtime-tracker");
            t.setDaemon(true); // no impide que la aplicación se cierre
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::safeTick, POLL.toMillis(), POLL.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * OJO: si una tarea programada con scheduleWithFixedDelay lanza una excepción, el executor
     * NO la vuelve a ejecutar nunca más (y en silencio). Por eso se captura todo aquí.
     */
    private void safeTick() {
        try {
            tick();
        } catch (Throwable e) {
            LOG.log(System.Logger.Level.ERROR, "Error comprobando los juegos abiertos", e);
        }
    }

    /** Una comprobación: actualiza cada juego vigilado y cierra las sesiones de los que ya no están. */
    synchronized void tick() throws SQLException {
        Instant now = clock.instant();
        for (Tracked t : List.copyOf(active.values())) {
            boolean alive = scanner.anyRunningIn(t.gameDir) || (t.process != null && t.process.isAlive());
            if (alive) {
                t.lastSeen = now;
                if (!Duration.between(t.lastHeartbeat, now).minus(HEARTBEAT_EVERY).isNegative()) {
                    sessions.heartbeat(t.sessionId, now);
                    t.lastHeartbeat = now;
                }
                continue;
            }
            if (now.isBefore(t.startedAt.plus(STARTUP_GRACE))) {
                continue; // todavía arrancando: esperar
            }
            if (t.lastSeen == null) {
                // Pasado el margen y nunca se vio: el juego no llegó a arrancar. No cuenta.
                sessions.delete(t.sessionId);
                end(t, Duration.ZERO);
            } else if (!Duration.between(t.lastSeen, now).minus(GAP_TOLERANCE).isNegative()) {
                sessions.finish(t.sessionId, t.lastSeen);
                end(t, Duration.between(t.startedAt, t.lastSeen));
            }
        }
    }

    private void end(Tracked t, Duration played) {
        active.remove(t.vnId);
        LOG.log(System.Logger.Level.INFO, "Sesión de {0} terminada: {1} min", t.vnId, played.toMinutes());
        listeners.forEach(l -> l.sessionEnded(t.vnId, played));
    }

    /**
     * Al cerrar el launcher: se paran las comprobaciones y se cierran las sesiones abiertas con la
     * última vez que se vio cada juego. (Si el juego sigue abierto, el tiempo que pase después de
     * cerrar el launcher no se cuenta: nadie lo está vigilando.)
     */
    public synchronized void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (Tracked t : List.copyOf(active.values())) {
            try {
                if (t.lastSeen == null) {
                    sessions.delete(t.sessionId);
                } else {
                    sessions.finish(t.sessionId, t.lastSeen);
                }
            } catch (SQLException e) {
                // Si falla, closeUnfinished() la cerrará al volver a arrancar con el último latido
                LOG.log(System.Logger.Level.WARNING, "No se pudo cerrar la sesión de " + t.vnId, e);
            }
            active.remove(t.vnId);
        }
    }
}

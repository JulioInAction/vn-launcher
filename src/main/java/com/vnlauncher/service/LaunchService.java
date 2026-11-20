package com.vnlauncher.service;

import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.model.LibraryEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lanza los juegos de la biblioteca.
 *
 * <p>Comandos que se ejecutan:
 * <ul>
 *   <li>Normal: {@code C:\Juegos\VN\game.exe}</li>
 *   <li>Con Locale Emulator (perfil por defecto, japonés): {@code LEProc.exe -run C:\Juegos\VN\game.exe}</li>
 *   <li>Con un perfil concreto de LE: {@code LEProc.exe -runas <GUID> C:\Juegos\VN\game.exe}</li>
 * </ul>
 * Siempre con la carpeta del juego como directorio de trabajo.
 *
 * <p>Pasamos cada parte del comando como un elemento separado de la lista (no un único String
 * "LEProc.exe -run C:\Mis Juegos\..."): así ProcessBuilder se encarga de las comillas y las rutas
 * con espacios funcionan sin problemas.
 */
public class LaunchService {

    private static final System.Logger LOG = System.getLogger(LaunchService.class.getName());

    private final LibraryRepository library;
    private final SettingsService settings;
    private final ProcessStarter starter;
    private final PlaytimeTracker tracker;
    private final Clock clock;

    public LaunchService(LibraryRepository library, SettingsService settings, ProcessStarter starter,
                         PlaytimeTracker tracker, Clock clock) {
        this.library = library;
        this.settings = settings;
        this.starter = starter;
        this.tracker = tracker;
        this.clock = clock;
    }

    /** Guarda el ejecutable elegido y si se usa Locale Emulator. */
    public void configure(String vnId, Path exePath, boolean useLocaleEmulator) throws SQLException {
        library.updateLaunchConfig(vnId, exePath == null ? null : exePath.toAbsolutePath().toString(),
                useLocaleEmulator);
    }

    public void setExecutable(String vnId, Path exePath) throws SQLException {
        library.updateExePath(vnId, exePath.toAbsolutePath().toString());
    }

    public void setUseLocaleEmulator(String vnId, boolean use) throws SQLException {
        library.updateUseLocaleEmulator(vnId, use);
    }

    public boolean isRunning(String vnId) {
        return tracker.isRunning(vnId);
    }

    /**
     * Lanza el juego, apunta la hora como "última partida" y empieza a medir el tiempo de juego.
     *
     * @throws LaunchException si falta algo por configurar o el sistema no pudo arrancarlo
     */
    public LaunchedGame launch(String vnId) throws LaunchException, SQLException {
        return launch(vnId, false);
    }

    /**
     * @param withoutLocaleEmulator true = lanzar esta vez sin Locale Emulator aunque la VN lo use
     *                              (botón "Jugar sin Locale Emulator" del aviso de que falta LEProc.exe)
     */
    public LaunchedGame launch(String vnId, boolean withoutLocaleEmulator) throws LaunchException, SQLException {
        if (tracker.isRunning(vnId)) {
            throw new LaunchException(LaunchException.Reason.ALREADY_RUNNING, "El juego ya está abierto.");
        }
        LibraryEntry entry = library.findByVnId(vnId)
                .orElseThrow(() -> new IllegalArgumentException(vnId + " no está en la biblioteca"));

        if (!entry.hasExecutable()) {
            throw new LaunchException(LaunchException.Reason.NO_EXECUTABLE,
                    "Elige primero el ejecutable del juego.");
        }
        Path exe = Path.of(entry.exePath());
        if (!Files.isRegularFile(exe)) {
            throw new LaunchException(LaunchException.Reason.EXECUTABLE_NOT_FOUND,
                    "No se encuentra el ejecutable:\n" + exe + "\n¿Has movido o desinstalado el juego?", exe);
        }
        Path gameDir = exe.toAbsolutePath().getParent();

        boolean useLe = entry.useLocaleEmulator() && !withoutLocaleEmulator;
        List<String> command = buildCommand(useLe, entry.leProfile(), exe);
        Instant now = clock.instant();
        LaunchedGame launched = start(vnId, command, exe, gameDir, now, !useLe);
        library.updateLastPlayed(vnId, now);
        tracker.track(launched);
        return launched;
    }

    /** Construye el comando según la configuración (con o sin Locale Emulator). */
    List<String> buildCommand(boolean useLocaleEmulator, String leProfile, Path exe)
            throws LaunchException, SQLException {
        List<String> command = new ArrayList<>();
        if (useLocaleEmulator) {
            Optional<Path> leProc = settings.localeEmulatorPath();
            if (leProc.isEmpty()) {
                throw new LaunchException(LaunchException.Reason.LOCALE_EMULATOR_NOT_CONFIGURED,
                        "Indica dónde está LEProc.exe para usar Locale Emulator.");
            }
            if (!Files.isRegularFile(leProc.get())) {
                throw new LaunchException(LaunchException.Reason.LOCALE_EMULATOR_NOT_FOUND,
                        "No se encuentra LEProc.exe en:\n" + leProc.get(), leProc.get());
            }
            command.add(leProc.get().toString());
            if (leProfile != null && !leProfile.isBlank()) {
                command.add("-runas");
                command.add(leProfile);
            } else {
                command.add("-run");
            }
        }
        command.add(exe.toAbsolutePath().toString());
        return command;
    }

    /**
     * Arranca el proceso. Si Windows responde "error=740" (el juego exige permisos de
     * administrador), lo reintenta con {@code cmd /c start}, que sí puede mostrar el aviso de
     * permisos (UAC) de Windows. ProcessBuilder no puede: llama a CreateProcess, que no eleva.
     *
     * @param allowElevationRetry false con Locale Emulator: ahí la elevación es cosa de LEProc
     */
    private LaunchedGame start(String vnId, List<String> command, Path exe, Path gameDir, Instant now,
                               boolean allowElevationRetry) throws LaunchException {
        LOG.log(System.Logger.Level.INFO, "Lanzando {0} en {1}", command, gameDir);
        try {
            return new LaunchedGame(vnId, starter.start(command, gameDir), gameDir, now, false);
        } catch (IOException e) {
            if (allowElevationRetry && requiresElevation(e)) {
                LOG.log(System.Logger.Level.INFO, "El juego pide permisos de administrador; reintentando con cmd /c start");
                try {
                    // start "" /D carpeta juego.exe   ("" es el título de la ventana: obligatorio
                    // ponerlo vacío, si no "start" tomaría la ruta entre comillas como título)
                    List<String> elevated = List.of("cmd.exe", "/c", "start", "", "/D", gameDir.toString(),
                            exe.toAbsolutePath().toString());
                    return new LaunchedGame(vnId, starter.start(elevated, gameDir), gameDir, now, true);
                } catch (IOException retryError) {
                    e.addSuppressed(retryError);
                }
            }
            throw new LaunchException(LaunchException.Reason.START_FAILED,
                    "No se pudo arrancar el juego: " + e.getMessage(), e);
        }
    }

    /** CreateProcess error=740: "The requested operation requires elevation". */
    static boolean requiresElevation(IOException e) {
        return e.getMessage() != null && e.getMessage().contains("error=740");
    }
}

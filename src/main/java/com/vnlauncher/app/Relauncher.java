package com.vnlauncher.app;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reinicia la aplicación (después de preparar una copia para restaurar). Se abre un proceso nuevo
 * ANTES de cerrar este, y el nuevo espera a que este termine: así nunca hay dos usando la base de
 * datos a la vez.
 *
 * <p>El proceso nuevo recibe {@code --esperar-pid=<pid de este>} (JavaFX lo da en
 * {@code getParameters().getNamed()}).
 */
final class Relauncher {

    private static final System.Logger LOG = System.getLogger(Relauncher.class.getName());

    /** Nombre del parámetro: {@code --esperar-pid=1234}. */
    static final String WAIT_FOR_PID = "esperar-pid";
    private static final String WAIT_PREFIX = "--" + WAIT_FOR_PID + "=";
    /** Lo más que espera la app nueva a que se cierre la anterior. */
    static final Duration MAX_WAIT = Duration.ofSeconds(15);

    private Relauncher() {
    }

    /**
     * Abre una VN Launcher nueva que espera a que esta se cierre.
     *
     * @return false si no se ha podido (el usuario tendrá que volver a abrirla a mano)
     */
    static boolean start() {
        ProcessHandle self = ProcessHandle.current();
        Optional<List<String>> command = command(System.getProperty("jpackage.app-path"),
                self.info().command(), self.info().arguments(), self.pid());
        if (command.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "No se sabe cómo volver a abrir la aplicación");
            return false;
        }
        try {
            // Su salida no interesa aquí (tiene su propio registro); si se heredara, quedaría atada
            // a la consola de este proceso, que está a punto de cerrarse
            new ProcessBuilder(command.get())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            LOG.log(System.Logger.Level.INFO, "Reiniciando: {0}", command.get().getFirst());
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo volver a abrir la aplicación", e);
            return false;
        }
    }

    /**
     * El comando para volver a abrir la app:
     * <ul>
     *   <li>instalada: el .exe del instalador (jpackage lo deja en la propiedad jpackage.app-path);</li>
     *   <li>en desarrollo: el mismo java con los mismos argumentos.</li>
     * </ul>
     * En los dos casos se añade {@code --esperar-pid}, y se quita el de un reinicio anterior.
     *
     * @return vacío si no hay forma de saberlo (p. ej. en Windows, sin instalar, Java no da los argumentos)
     */
    static Optional<List<String>> command(String appPath, Optional<String> javaCommand,
                                          Optional<String[]> arguments, long pid) {
        List<String> command = new ArrayList<>();
        if (appPath != null && !appPath.isBlank()) {
            command.add(appPath);
        } else if (javaCommand.isPresent() && arguments.isPresent()) {
            command.add(javaCommand.get());
            for (String argument : arguments.get()) {
                if (!argument.startsWith(WAIT_PREFIX)) {
                    command.add(argument);
                }
            }
        } else {
            return Optional.empty();
        }
        command.add(WAIT_PREFIX + pid);
        return Optional.of(command);
    }

    /**
     * En la app nueva: espera (como mucho {@link #MAX_WAIT}) a que se cierre la anterior.
     *
     * @param pidText el valor de --esperar-pid, o null si no es un reinicio
     */
    static void waitForPrevious(String pidText) {
        if (pidText == null) {
            return;
        }
        try {
            Optional<ProcessHandle> previous = ProcessHandle.of(Long.parseLong(pidText));
            if (previous.isPresent()) {
                previous.get().onExit().get(MAX_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (NumberFormatException | ExecutionException | TimeoutException e) {
            // Se sigue igualmente: el bloqueo de instancia única también espera un poco
            LOG.log(System.Logger.Level.WARNING, "No se pudo esperar a la ventana anterior (" + pidText + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.vnlauncher.ui;

import com.vnlauncher.api.VndbException;
import com.vnlauncher.service.LaunchException;
import javafx.application.Platform;
import javafx.scene.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Punto único para los errores INESPERADOS: los guarda en el registro y enseña el diálogo «Algo ha
 * fallado» (docs/diseño/capturas/error-general-*.png).
 *
 * <p>Los errores esperados no pasan por aquí: sin conexión o el límite de VNDB tienen sus avisos
 * dentro de cada pantalla ({@link #isExpected}). Lo que llega aquí es lo que no debería pasar: un
 * fallo de la base de datos, un fallo de programación...
 *
 * <p>Llegan por tres caminos:
 * <ul>
 *   <li>{@link #report}: una pantalla sabe qué estaba haciendo («No se ha podido guardar la ruta»);</li>
 *   <li>el manejador global ({@link #install}): excepciones que nadie captura, en cualquier hilo
 *       (también el de JavaFX, que pasa ahí las de los manejadores de eventos);</li>
 *   <li>las tareas en segundo plano sin manejador de error propio (ver {@link BackgroundTasks}).</li>
 * </ul>
 *
 * <p>Se puede llamar desde cualquier hilo: el diálogo se abre siempre en el de la interfaz. Solo hay
 * uno abierto a la vez; si mientras tanto llegan más errores, solo se registran (un error que se
 * repite en bucle no llena la pantalla de diálogos).
 */
public final class ErrorReporter {

    private static final System.Logger LOG = System.getLogger(ErrorReporter.class.getName());

    static final String UNEXPECTED = "Ha ocurrido un error inesperado.";
    static final String ADVICE_DATA_SAFE = "Tus datos no se han perdido: vuelve a intentarlo y, si sigue "
            + "pasando, adjunta el registro al informar del problema.";
    static final String ADVICE_GENERIC = "Si vuelve a pasar, adjunta el registro al informar del problema.";
    private static final String UNCAUGHT_LOG = "Error no capturado en el hilo %s";

    /** Mismo error repetido en menos de esto: no se vuelve a escribir en el registro. */
    private static final long REPEAT_WINDOW_MS = 2000;

    private static volatile Path logFile;
    private static volatile Consumer<String> folderOpener = uri -> { };
    /** Un nodo de la ventana principal, para centrar el diálogo y taparla. null = aún no hay ventana. */
    private static volatile Node owner;
    /** Solo se usa en el hilo de la interfaz. */
    private static boolean showing;
    private static String lastSignature;
    private static long lastLoggedAt;

    private ErrorReporter() {
    }

    /**
     * Activa el manejador global. Llamar al arrancar, después de preparar el registro.
     *
     * @param folderOpener abre una carpeta (una URI file:/...) en el explorador de archivos
     */
    public static void install(Path currentLogFile, Consumer<String> folderOpener) {
        ErrorReporter.logFile = currentLogFile;
        ErrorReporter.folderOpener = folderOpener;
        Thread.setDefaultUncaughtExceptionHandler(ErrorReporter::uncaught);
    }

    /** La ventana principal ya existe: los diálogos se centran sobre ella. */
    public static void setOwner(Node node) {
        owner = node;
    }

    /**
     * Un error inesperado haciendo algo concreto.
     *
     * @param message frase completa que dice qué ha fallado: «No se ha podido guardar la ruta.»
     */
    public static void report(String message, Throwable error) {
        log(message, error);
        show(message, ADVICE_DATA_SAFE, error);
    }

    /** true para los errores que tienen su propio aviso (sin conexión, VNDB, al lanzar un juego). */
    public static boolean isExpected(Throwable error) {
        return error instanceof VndbException || error instanceof IOException
                || error instanceof LaunchException || error instanceof InterruptedException;
    }

    /** Dónde está el registro (null si no se pudo preparar). */
    public static Path logFile() {
        return logFile;
    }

    /** «Abrir carpeta de registros». */
    static void openLogsFolder() {
        Path file = logFile;
        if (file != null) {
            folderOpener.accept(file.getParent().toUri().toString());
        }
    }

    /**
     * Error al arrancar, cuando aún no hay ventana: el diálogo sale solo, centrado en la pantalla.
     * Llamar en el hilo de la interfaz; espera a que se cierre.
     */
    public static void showStartupError(String message, Throwable error) {
        log(message, error);
        ErrorDialog.show(null, message, ADVICE_GENERIC, error);
    }

    private static void uncaught(Thread thread, Throwable error) {
        log(String.format(UNCAUGHT_LOG, thread.getName()), error);
        show(UNEXPECTED, ADVICE_GENERIC, error);
    }

    /** Escribe el error en el registro, salvo que sea el mismo de hace un momento. */
    private static synchronized void log(String message, Throwable error) {
        String signature = error + Arrays.toString(error.getStackTrace());
        long now = System.currentTimeMillis();
        if (signature.equals(lastSignature) && now - lastLoggedAt < REPEAT_WINDOW_MS) {
            return;
        }
        lastSignature = signature;
        lastLoggedAt = now;
        LOG.log(System.Logger.Level.ERROR, message, error);
    }

    private static void show(String message, String advice, Throwable error) {
        try {
            // runLater también desde el hilo de la interfaz: si el error salta durante una
            // animación o el cálculo del layout, JavaFX no deja abrir ahí un diálogo modal
            Platform.runLater(() -> showNow(message, advice, error));
        } catch (IllegalStateException toolkitNotRunning) {
            // JavaFX aún no ha arrancado o ya se ha cerrado: basta con el registro
        }
    }

    private static void showNow(String message, String advice, Throwable error) {
        if (showing) {
            return;
        }
        showing = true;
        try {
            ErrorDialog.show(owner, message, advice, error);
        } catch (RuntimeException e) {
            // Si ni el diálogo de error se puede abrir, que al menos quede en el registro
            LOG.log(System.Logger.Level.ERROR, "No se pudo mostrar el diálogo de error", e);
        } finally {
            showing = false;
        }
    }
}

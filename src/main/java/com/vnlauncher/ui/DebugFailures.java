package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import javafx.application.Platform;

import java.sql.SQLException;

/**
 * Errores provocados a propósito, para probar el registro y el diálogo «Algo ha fallado» sin tener
 * que romper nada (igual que -Dvnlauncher.vndb.maxRequests sirve para probar el aviso de límite).
 * Sin la propiedad no hace nada.
 *
 * <pre>
 * -Dvnlauncher.debug.fallo=arranque  error al preparar la base de datos (diálogo sin ventana)
 * -Dvnlauncher.debug.fallo=fx        excepción en el hilo de la interfaz (manejador global)
 * -Dvnlauncher.debug.fallo=tarea     tarea en segundo plano que falla («No se ha podido guardar la ruta.»)
 * -Dvnlauncher.debug.fallo=hilo      excepción suelta en un hilo de fondo (manejador global)
 * </pre>
 */
public final class DebugFailures {

    private static final String PROPERTY = "vnlauncher.debug.fallo";
    private static final String MESSAGE = "Fallo de prueba (%s=%s)";

    private DebugFailures() {
    }

    /** Antes de preparar la base de datos. */
    public static void atStartup() throws SQLException {
        if ("arranque".equals(mode())) {
            throw new SQLException(message());
        }
    }

    /** Con la ventana ya abierta. */
    public static void afterStartup(AppContext ctx) {
        String mode = mode();
        if (mode == null) {
            return;
        }
        switch (mode) {
            case "fx" -> Platform.runLater(() -> {
                throw new IllegalStateException(message());
            });
            case "tarea" -> BackgroundTasks.run(ctx.background(), () -> {
                        throw new SQLException(message());
                    },
                    ignored -> { },
                    "No se ha podido guardar la ruta.");
            case "hilo" -> ctx.background().execute(() -> {
                throw new IllegalStateException(message());
            });
            default -> { }
        }
    }

    private static String mode() {
        return System.getProperty(PROPERTY);
    }

    private static String message() {
        return String.format(MESSAGE, PROPERTY, mode());
    }
}

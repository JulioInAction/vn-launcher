package com.vnlauncher.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Texto de «Detalles técnicos» del diálogo de error: tipo y mensaje, las primeras líneas de la traza
 * y las causas encadenadas, y al final dónde está el registro completo.
 *
 * <pre>
 * java.sql.SQLException: [SQLITE_BUSY] The database file is locked
 *   at com.vnlauncher.db.repo.RouteRepository.save(RouteRepository.java:57)
 *   … (14 más)
 * Causado por: java.io.IOException: ...
 * Registro: %LOCALAPPDATA%\VNLauncher\logs\vn-launcher0.log
 * </pre>
 *
 * Es corto a propósito: se lee en el diálogo y se pega en un informe. La traza entera ya está en
 * el registro.
 */
public final class ErrorDetails {

    /** Líneas de traza que se enseñan de cada excepción. */
    static final int FRAMES = 6;
    /** Causas encadenadas que se enseñan como mucho. */
    static final int CAUSES = 3;

    /** clase.método(Archivo.java:línea), sin el "módulo@versión/" que Java pone delante. */
    private static final String FRAME = "  at %s.%s(%s:%d)%n";
    private static final String FRAME_NO_LINE = "  at %s.%s(%s)%n";
    private static final String UNKNOWN_SOURCE = "?";
    private static final String MORE_FRAMES = "  … (%d más)%n";
    private static final String CAUSED_BY = "Causado por: %s%n";
    private static final String LOG_LOCATION = "Registro: %s";

    private ErrorDetails() {
    }

    /** @param logLocation dónde está el registro, ya preparado para enseñar (ver DisplayPaths) */
    public static String format(Throwable error, String logLocation) {
        StringBuilder text = new StringBuilder();
        // Las causas pueden formar un ciclo (A causada por B causada por A): se cortan con un Set
        Set<Throwable> seen = new HashSet<>();
        Throwable current = error;
        int depth = 0;
        while (current != null && seen.add(current) && depth <= CAUSES) {
            text.append(depth == 0 ? current + System.lineSeparator()
                    : String.format(CAUSED_BY, current));
            StackTraceElement[] frames = current.getStackTrace();
            for (int i = 0; i < Math.min(FRAMES, frames.length); i++) {
                text.append(frame(frames[i]));
            }
            if (frames.length > FRAMES) {
                text.append(String.format(MORE_FRAMES, frames.length - FRAMES));
            }
            current = current.getCause();
            depth++;
        }
        return text.append(String.format(LOG_LOCATION, logLocation)).toString();
    }

    private static String frame(StackTraceElement f) {
        String file = f.getFileName() == null ? UNKNOWN_SOURCE : f.getFileName();
        return f.getLineNumber() >= 0
                ? String.format(FRAME, f.getClassName(), f.getMethodName(), file, f.getLineNumber())
                : String.format(FRAME_NO_LINE, f.getClassName(), f.getMethodName(), file);
    }
}

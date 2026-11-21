package com.vnlauncher.util;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos de la interfaz que no dependen de los datos de idioma de Java (el instalador no incluye
 * el módulo jdk.localedata): fechas con un patrón numérico fijo y tamaños con coma decimal.
 */
public final class Formats {

    /** «21/11/2026, 10:42». Solo números: igual en cualquier idioma del sistema. */
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");
    /** «21/11/2026», sin la hora. */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String KB = "%d KB";
    private static final String MB = "%s MB";

    private Formats() {
    }

    /** 1536 → «2 KB», 2 516 582 → «2,4 MB». Menos de 1 MB, en KB sin decimales; desde 1 MB, con uno. */
    public static String fileSize(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 1) {
            return String.format(KB, Math.round(bytes / 1024.0));
        }
        // Locale.ROOT da siempre punto decimal; se cambia por la coma española
        return String.format(MB, String.format(Locale.ROOT, "%.1f", mb).replace('.', ','));
    }
}

package com.vnlauncher.app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Registro de la aplicación en archivos: logs/vn-launcher0.log (el actual), vn-launcher1.log...
 *
 * <p>Usa java.util.logging, que viene con Java (sin librerías nuevas). Los {@code System.Logger}
 * que ya hay por la app (VndbClient, PlaytimeTracker...) escriben aquí sin cambiarlos: cuando el
 * módulo java.logging está presente, System.Logger le pasa todo.
 *
 * <p><b>Rotación.</b> El {@link FileHandler} de Java escribe en el archivo 0 hasta que pasa de
 * {@link #LIMIT_BYTES}; entonces lo renombra a 1 (el 1 a 2...) y empieza otro 0. Como mucho hay
 * {@link #FILES} archivos: el más viejo se borra. Así el registro nunca pasa de 5 MB.
 */
public final class LogSetup {

    static final int LIMIT_BYTES = 1_000_000;
    static final int FILES = 5;
    /** %g = número de generación (0 = el actual). */
    static final String FILE_PATTERN = "vn-launcher%g.log";

    private LogSetup() {
    }

    /**
     * Prepara el registro. Llamar lo primero al arrancar.
     *
     * @return el archivo de registro actual (para «Abrir carpeta de registros» y los detalles)
     */
    public static Path configure(Path logsDir) throws IOException {
        Level level = Level.parse(System.getProperty("vnlauncher.log.level", "INFO"));
        configure(logsDir, LIMIT_BYTES, FILES, level);
        return currentFile(logsDir);
    }

    /** Variante para los tests (límite y número de archivos pequeños). Devuelve el handler añadido. */
    static FileHandler configure(Path logsDir, int limitBytes, int files, Level level) throws IOException {
        Files.createDirectories(logsDir);
        // En el patrón de FileHandler "%" es especial (%g, %u...): un "%" en la ruta se escribe "%%"
        String pattern = logsDir.toAbsolutePath().toString().replace("%", "%%")
                + logsDir.getFileSystem().getSeparator() + FILE_PATTERN;
        // append = true: al arrancar sigue escribiendo en el mismo archivo (no borra lo anterior)
        FileHandler handler = new FileHandler(pattern, limitBytes, files, true);
        handler.setEncoding(StandardCharsets.UTF_8.name()); // tildes y japonés bien escritos
        handler.setFormatter(new LineFormatter());
        handler.setLevel(Level.ALL);

        Logger root = Logger.getLogger(""); // el padre de todos los loggers
        root.setLevel(level);
        root.addHandler(handler);
        return handler;
    }

    public static Path currentFile(Path logsDir) {
        return logsDir.resolve(FILE_PATTERN.replace("%g", "0"));
    }

    /**
     * Una línea por mensaje, y debajo la traza si hay excepción:
     * <pre>2026-11-21 10:42:03.512 INFO    com.vnlauncher.api.VndbClient: Petición a VNDB: POST ...</pre>
     */
    static final class LineFormatter extends Formatter {

        /** Patrón fijo, sin nombres de meses: no depende del idioma del sistema. */
        private static final DateTimeFormatter TIME =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
        private static final String LINE = "%s %-7s %s: %s%n";

        @Override
        public String format(LogRecord record) {
            // formatMessage sustituye los {0}, {1}... de los mensajes con parámetros
            String line = String.format(LINE, TIME.format(Instant.ofEpochMilli(record.getMillis())),
                    record.getLevel().getName(), record.getLoggerName(), formatMessage(record));
            if (record.getThrown() == null) {
                return line;
            }
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            return line + trace;
        }
    }
}

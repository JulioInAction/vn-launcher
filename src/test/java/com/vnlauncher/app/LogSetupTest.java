package com.vnlauncher.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LogSetupTest {

    @TempDir
    Path tempDir;

    private FileHandler handler;
    private final Level previousLevel = Logger.getLogger("").getLevel();

    /** El registro es global (el logger raíz): hay que dejarlo como estaba para los demás tests. */
    @AfterEach
    void tearDown() {
        if (handler != null) {
            Logger.getLogger("").removeHandler(handler);
            handler.close();
        }
        Logger.getLogger("").setLevel(previousLevel);
    }

    private List<String> logFiles(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".log")).sorted().toList();
        }
    }

    @Test
    void createsTheFolderAndWritesInUtf8() throws Exception {
        Path logs = tempDir.resolve("logs");
        handler = LogSetup.configure(logs, 100_000, 5, Level.INFO);

        System.getLogger("prueba").log(System.Logger.Level.INFO, "Petición a VNDB: 殻ノ少女 {0}", 42);
        handler.flush();

        Path current = LogSetup.currentFile(logs);
        assertEquals("vn-launcher0.log", current.getFileName().toString());
        String text = Files.readString(current, StandardCharsets.UTF_8);
        assertTrue(text.contains("INFO    prueba: Petición a VNDB: 殻ノ少女 42"), text);
    }

    @Test
    void rotatesAndNeverKeepsMoreThanTheMaximumOfFiles() throws Exception {
        Path logs = tempDir.resolve("logs");
        handler = LogSetup.configure(logs, 2_000, 3, Level.INFO);

        System.Logger log = System.getLogger("rotacion");
        for (int i = 0; i < 300; i++) {
            log.log(System.Logger.Level.INFO, "Línea de relleno número {0} para llenar el archivo", i);
        }
        handler.flush();

        assertEquals(List.of("vn-launcher0.log", "vn-launcher1.log", "vn-launcher2.log"), logFiles(logs),
                "Como mucho 3: el más viejo se borra");
        assertTrue(Files.size(logs.resolve("vn-launcher1.log")) <= 2_000 + 200, "Cada uno, más o menos el límite");
        assertTrue(Files.readString(LogSetup.currentFile(logs)).contains("número 299"), "El 0 es el más reciente");
    }

    @Test
    void belowTheLevelNothingIsWritten() throws Exception {
        Path logs = tempDir.resolve("logs");
        handler = LogSetup.configure(logs, 100_000, 5, Level.INFO);

        System.getLogger("prueba").log(System.Logger.Level.DEBUG, "detalle que no interesa");
        handler.flush();

        assertFalse(Files.readString(LogSetup.currentFile(logs)).contains("no interesa"));
    }

    @Test
    void exceptionsGoBelowTheLineWithTheirTrace() {
        LogRecord record = new LogRecord(Level.SEVERE, "No se ha podido guardar la ruta.");
        record.setLoggerName("com.vnlauncher.ui.ErrorReporter");
        record.setThrown(new IllegalStateException("roto"));

        String text = new LogSetup.LineFormatter().format(record);

        String firstLine = text.lines().findFirst().orElseThrow();
        assertTrue(firstLine.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} SEVERE  "
                + "com\\.vnlauncher\\.ui\\.ErrorReporter: No se ha podido guardar la ruta\\."), firstLine);
        assertTrue(text.contains("java.lang.IllegalStateException: roto"));
        // Desde un módulo, cada línea es "\tat com.vnlauncher/com.vnlauncher.app.LogSetupTest...."
        assertTrue(text.contains("LogSetupTest.exceptionsGoBelowTheLineWithTheirTrace"), "Con la traza:\n" + text);
    }
}

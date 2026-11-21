package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorDetailsTest {

    private static final String LOG = "%LOCALAPPDATA%\\VNLauncher\\logs\\vn-launcher0.log";

    private static StackTraceElement frame(int line) {
        return new StackTraceElement("com.vnlauncher.db.repo.RouteRepository", "save", "RouteRepository.java", line);
    }

    @Test
    void typeMessageFirstFramesAndWhereTheLogIs() {
        SQLException error = new SQLException("[SQLITE_BUSY] The database file is locked");
        StackTraceElement[] frames = new StackTraceElement[10];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = frame(50 + i);
        }
        error.setStackTrace(frames);

        List<String> lines = ErrorDetails.format(error, LOG).lines().toList();

        assertEquals("java.sql.SQLException: [SQLITE_BUSY] The database file is locked", lines.getFirst());
        assertEquals("  at com.vnlauncher.db.repo.RouteRepository.save(RouteRepository.java:50)", lines.get(1));
        assertEquals(1 + ErrorDetails.FRAMES + 1 + 1, lines.size(), "Mensaje, 6 líneas, «… (4 más)» y el registro");
        assertEquals("  … (4 más)", lines.get(1 + ErrorDetails.FRAMES));
        assertEquals("Registro: " + LOG, lines.getLast());
    }

    @Test
    void framesWithoutModuleNameAndWithoutLineNumber() {
        IllegalStateException error = new IllegalStateException("x");
        error.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(null, "com.vnlauncher", "0.1.0", "com.vnlauncher.ui.A", "run", "A.java", 12),
                new StackTraceElement("java.lang.Thread", "run", null, -2)});

        List<String> lines = ErrorDetails.format(error, LOG).lines().toList();

        assertEquals("  at com.vnlauncher.ui.A.run(A.java:12)", lines.get(1), "Sin «com.vnlauncher@0.1.0/»");
        assertEquals("  at java.lang.Thread.run(?)", lines.get(2));
    }

    @Test
    void causesAreShownBelow() {
        IOException cause = new IOException("disco lleno");
        cause.setStackTrace(new StackTraceElement[] {frame(1)});
        IllegalStateException error = new IllegalStateException("No se pudo copiar", cause);
        error.setStackTrace(new StackTraceElement[] {frame(2)});

        String text = ErrorDetails.format(error, LOG);

        assertTrue(text.contains("Causado por: java.io.IOException: disco lleno"), text);
        assertTrue(text.indexOf("disco lleno") > text.indexOf("No se pudo copiar"), "La causa, después");
    }

    @Test
    void aCycleOfCausesDoesNotLoopForever() {
        IllegalStateException a = new IllegalStateException("a");
        IllegalStateException b = new IllegalStateException("b", a);
        a.initCause(b); // a → b → a → ...

        String text = ErrorDetails.format(a, LOG);

        assertEquals(1, text.lines().filter(l -> l.startsWith("Causado por:")).count());
        assertTrue(text.endsWith("Registro: " + LOG));
    }
}

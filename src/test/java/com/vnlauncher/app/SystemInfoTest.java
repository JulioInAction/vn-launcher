package com.vnlauncher.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SystemInfoTest {

    @Test
    void everythingNeededToReportAProblem() {
        String text = SystemInfo.text("1.0.0", "26.0.1", "Eclipse Adoptium", "26.0.2", "Windows 11", "10.0",
                "amd64", 7, "%LOCALAPPDATA%\\VNLauncher");

        assertEquals("""
                VN Launcher 1.0.0
                Java: 26.0.1 (Eclipse Adoptium)
                JavaFX: 26.0.2
                Sistema operativo: Windows 11 10.0 (amd64)
                Versión del esquema de la base de datos: 7
                Carpeta de datos: %LOCALAPPDATA%\\VNLauncher""", text);
    }

    @Test
    void currentUsesThisComputer() {
        String text = SystemInfo.current(5, Path.of(System.getProperty("java.io.tmpdir")));

        assertTrue(text.contains(System.getProperty("java.version")));
        assertTrue(text.contains("esquema de la base de datos: 5"));
        assertFalse(text.contains("%s"), "Plantilla rellena del todo");
    }
}

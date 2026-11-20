package com.vnlauncher.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppInfoTest {

    @Test
    void versionComesFromThePomAndIsOnlyNumbers() {
        // Si el filtrado de Maven no se aplicara, saldría "${project.version}" literalmente
        assertTrue(AppInfo.VERSION.matches("\\d+\\.\\d+\\.\\d+"),
                "jpackage solo acepta versiones como 1.2.3, y esta es: " + AppInfo.VERSION);
    }
}

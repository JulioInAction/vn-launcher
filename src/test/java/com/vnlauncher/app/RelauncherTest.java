package com.vnlauncher.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RelauncherTest {

    @Test
    void installedItReopensTheExeOfTheInstaller() {
        assertEquals(List.of("C:\\Program Files\\VN Launcher\\VN Launcher.exe", "--esperar-pid=42"),
                Relauncher.command("C:\\Program Files\\VN Launcher\\VN Launcher.exe",
                        Optional.of("java.exe"), Optional.empty(), 42).orElseThrow());
    }

    @Test
    void inDevelopmentTheSameJavaWithTheSameArgumentsButOnlyTheNewPid() {
        String[] arguments = {"-Dvnlauncher.home=/tmp/vn", "-m", "com.vnlauncher/com.vnlauncher.app.Main",
                "--esperar-pid=7"};

        assertEquals(List.of("/usr/bin/java", "-Dvnlauncher.home=/tmp/vn", "-m",
                        "com.vnlauncher/com.vnlauncher.app.Main", "--esperar-pid=42"),
                Relauncher.command(null, Optional.of("/usr/bin/java"), Optional.of(arguments), 42).orElseThrow());
    }

    @Test
    void withoutKnowingTheCommandItDoesNotGuess() {
        assertTrue(Relauncher.command("", Optional.of("java.exe"), Optional.empty(), 42).isEmpty(),
                "Windows sin instalar: Java no da los argumentos");
    }
}

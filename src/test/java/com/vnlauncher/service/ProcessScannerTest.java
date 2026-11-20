package com.vnlauncher.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba el escáner con un proceso REAL: copia un ejecutable del sistema dentro de una carpeta
 * de "juego" y lo arranca desde ahí. (Ojo: en Linux un script aparecería como /bin/sh, su
 * intérprete; por eso se copia un ejecutable de verdad.)
 */
class ProcessScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsAProcessRunningFromTheGameFolder() throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path source = windows ? Path.of("C:\\Windows\\System32\\PING.EXE") : Path.of("/bin/sleep");
        assumeTrue(Files.isExecutable(source), "No se encuentra " + source + " en este sistema");

        Path gameDir = Files.createDirectories(tempDir.resolve("Mis Juegos/Juego"));
        Path exe = Files.copy(source, gameDir.resolve(windows ? "game.exe" : "game"));
        exe.toFile().setExecutable(true);
        Path otherDir = Files.createDirectories(tempDir.resolve("Otra carpeta"));

        ProcessScanner scanner = ProcessScanner.system();
        assertFalse(scanner.anyRunningIn(gameDir), "Antes de arrancarlo no hay nada");

        List<String> command = windows
                ? List.of(exe.toString(), "-n", "30", "127.0.0.1")   // ping: unos 30 s
                : List.of(exe.toString(), "30");                      // sleep 30
        Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertTrue(scanner.anyRunningIn(gameDir), "El proceso se ejecuta desde la carpeta del juego");
            assertFalse(scanner.anyRunningIn(otherDir));
        } finally {
            process.destroy();
            process.waitFor();
        }
        assertFalse(scanner.anyRunningIn(gameDir), "Al cerrarlo deja de verse");
    }

    @Test
    void normalizeIgnoresCaseAndSlashesOnlyOnWindows() {
        assertEquals("c:/juegos/vn/game.exe", ProcessScanner.normalize("C:\\Juegos\\VN\\Game.exe", true));
        assertEquals("/home/Juegos/game", ProcessScanner.normalize("/home/Juegos/game", false));
    }
}

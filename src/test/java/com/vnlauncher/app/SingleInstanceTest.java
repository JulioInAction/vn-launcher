package com.vnlauncher.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SingleInstanceTest {

    @TempDir
    Path tempDir;

    @Test
    void onlyOneAtATimeAndFreeAgainAfterClosing() throws Exception {
        Path lock = tempDir.resolve("datos").resolve("vn-launcher.lock");

        Optional<SingleInstance> first = SingleInstance.acquire(lock, Duration.ZERO);
        assertTrue(first.isPresent(), "Crea la carpeta y el archivo si no están");

        long start = System.nanoTime();
        assertTrue(SingleInstance.acquire(lock, Duration.ofMillis(500)).isEmpty(), "La segunda no puede");
        assertTrue(System.nanoTime() - start >= Duration.ofMillis(400).toNanos(), "Reintenta mientras le dejan");

        first.get().close();
        Optional<SingleInstance> again = SingleInstance.acquire(lock, Duration.ZERO);
        assertTrue(again.isPresent(), "Al cerrar la primera, ya se puede");
        again.get().close();
    }
}

package com.vnlauncher.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Responde a "¿hay algún proceso abierto cuyo ejecutable esté dentro de esta carpeta?".
 *
 * <p>Es la clave para medir bien el tiempo: muchos juegos (y Locale Emulator) arrancan un
 * proceso, este lanza OTRO y se cierra. Si solo vigiláramos el proceso que arrancamos nosotros,
 * la sesión "terminaría" a los dos segundos. Mirando qué hay ejecutándose desde la carpeta del
 * juego da igual qué proceso concreto sea.
 *
 * <p>Interfaz para poder usar una versión falsa en los tests.
 */
@FunctionalInterface
public interface ProcessScanner {

    boolean anyRunningIn(Path directory);

    /** Implementación real: recorre todos los procesos del sistema con ProcessHandle. */
    static ProcessScanner system() {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        return directory -> {
            String dir = normalize(directory.toAbsolutePath().toString(), windows);
            String prefix = dir.endsWith("/") ? dir : dir + "/";
            // allProcesses() devuelve un Stream con TODOS los procesos visibles.
            // info().command() es la ruta completa del ejecutable (Optional: puede no estar
            // disponible, p. ej. para procesos de otros usuarios).
            return ProcessHandle.allProcesses()
                    .map(p -> p.info().command())
                    .flatMap(Optional::stream)
                    .map(cmd -> normalize(cmd, windows))
                    .anyMatch(cmd -> cmd.startsWith(prefix));
        };
    }

    /**
     * Para comparar rutas: barras "/" siempre y, en Windows, minúsculas (en Windows
     * "C:\Juegos" y "c:\juegos" son la misma carpeta).
     */
    static String normalize(String path, boolean windows) {
        String p = path.replace('\\', '/');
        return windows ? p.toLowerCase(Locale.ROOT) : p;
    }
}

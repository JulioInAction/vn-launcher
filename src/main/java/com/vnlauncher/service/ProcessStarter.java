package com.vnlauncher.service;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.List;

/**
 * "Arranca este comando en esta carpeta". Es una interfaz para poder sustituirla en los tests:
 * así comprobamos QUÉ comando se lanzaría sin ejecutar ningún juego de verdad.
 */
@FunctionalInterface
public interface ProcessStarter {

    Process start(List<String> command, Path workingDirectory) throws IOException;

    /** Implementación real con ProcessBuilder. */
    static ProcessStarter system() {
        return (command, workingDirectory) -> new ProcessBuilder(command)
                // El directorio de trabajo del juego: muchos juegos buscan sus datos con rutas
                // relativas ("data/scenario.dat") y fallan si se lanzan desde otra carpeta.
                .directory(workingDirectory.toFile())
                // IMPORTANTE: por defecto Java conecta la salida del proceso a una "tubería" que
                // alguien tiene que leer. Si nadie la lee y el juego escribe mucho en consola,
                // la tubería se llena y el juego SE QUEDA BLOQUEADO. Como no nos interesa su
                // salida, la descartamos.
                .redirectOutput(Redirect.DISCARD)
                .redirectError(Redirect.DISCARD)
                .start();
    }
}

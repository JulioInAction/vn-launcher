package com.vnlauncher.service;

import com.vnlauncher.model.LibraryEntry;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * ¿Siguen ahí el ejecutable del juego y su programa de configuración? (fase 7: ejecutables
 * perdidos). Un juego movido, desinstalado o en un disco que no está conectado deja la ruta
 * apuntando a nada: la biblioteca lo marca con «No encontrado» y la ficha avisa.
 *
 * <p>Mira el disco, así que se llama en segundo plano (una unidad de red lenta podría tardar).
 *
 * @param exe    el ejecutable del juego
 * @param config el programa de configuración
 */
public record GameFiles(FileStatus exe, FileStatus config) {

    public enum FileStatus {
        /** No hay ruta guardada: no se ha elegido (eso no es "perdido"). */
        NOT_SET,
        FOUND,
        /** Hay ruta, pero no hay ningún archivo ahí (o la ruta ni siquiera es válida). */
        MISSING
    }

    /** Comprueba los dos archivos de una VN. */
    public static GameFiles of(LibraryEntry entry) {
        return new GameFiles(check(entry.exePath(), Files::isRegularFile),
                check(entry.configExePath(), Files::isRegularFile));
    }

    /**
     * @param exists cómo se mira si el archivo está (los tests pasan uno propio)
     */
    public static FileStatus check(String path, Predicate<Path> exists) {
        if (path == null || path.isBlank()) {
            return FileStatus.NOT_SET;
        }
        try {
            return exists.test(Path.of(path)) ? FileStatus.FOUND : FileStatus.MISSING;
        } catch (InvalidPathException e) {
            // Una ruta que ni se puede leer (p. ej. guardada en otro sistema) tampoco lleva a nada
            return FileStatus.MISSING;
        }
    }

    public boolean exeMissing() {
        return exe == FileStatus.MISSING;
    }

    public boolean configMissing() {
        return config == FileStatus.MISSING;
    }
}

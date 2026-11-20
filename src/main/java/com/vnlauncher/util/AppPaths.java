package com.vnlauncher.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sabe dónde guarda la aplicación sus datos (base de datos e imágenes).
 *
 * <p>En Windows usamos %LOCALAPPDATA%\VNLauncher (p. ej. C:\Users\tu\AppData\Local\VNLauncher):
 * es la carpeta pensada para datos de programa del usuario que no se sincronizan.
 * En otros sistemas (mi entorno de pruebas es Linux) caemos en ~/.vnlauncher.
 *
 * <p>Se puede forzar otra carpeta con la propiedad de sistema {@code vnlauncher.home}
 * (p. ej. {@code java -Dvnlauncher.home=C:\temp\vn ...}). Ojo: con {@code mvn javafx:run}
 * no funciona pasarla por línea de comandos, porque el plugin arranca la app en otro proceso Java.
 */
public final class AppPaths {

    private final Path root;

    public AppPaths(Path root) {
        this.root = root;
    }

    /** Resuelve la carpeta según el sistema operativo o la propiedad vnlauncher.home. */
    public static AppPaths fromEnvironment() {
        String override = System.getProperty("vnlauncher.home");
        if (override != null && !override.isBlank()) {
            return new AppPaths(Path.of(override));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return new AppPaths(Path.of(localAppData, "VNLauncher"));
        }
        return new AppPaths(Path.of(System.getProperty("user.home"), ".vnlauncher"));
    }

    /** Crea las carpetas si no existen. Llamar una vez al arrancar. */
    public void createDirectories() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(imagesDir());
        } catch (IOException e) {
            // UncheckedIOException: si no podemos crear la carpeta de datos la app no puede funcionar,
            // no tiene sentido obligar a cada llamador a capturar una excepción comprobada.
            throw new UncheckedIOException("No se pudo crear la carpeta de datos " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    public Path databaseFile() {
        return root.resolve("library.db");
    }

    public Path imagesDir() {
        return root.resolve("images");
    }
}

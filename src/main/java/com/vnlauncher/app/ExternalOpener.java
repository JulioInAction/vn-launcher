package com.vnlauncher.app;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Abre cosas fuera de la app: una dirección web en el navegador y una carpeta en el explorador de
 * archivos.
 *
 * <p>Las carpetas llegan como una URI {@code file:/...}. {@code HostServices.showDocument} de JavaFX
 * las manda al NAVEGADOR en Windows (instalada, la app se veía abriendo «Abrir carpeta» en Chrome),
 * así que se abren con el programa del sistema: {@code explorer.exe}, {@code open} o {@code xdg-open}.
 */
public final class ExternalOpener {

    private static final System.Logger LOG = System.getLogger(ExternalOpener.class.getName());

    private ExternalOpener() {
    }

    /**
     * @param target  {@code https://...} (navegador) o {@code file:/...} (carpeta o archivo en el explorador)
     * @param browser abre una dirección web (HostServices.showDocument)
     */
    public static void open(String target, Consumer<String> browser) {
        if (target == null || !target.startsWith("file:")) {
            browser.accept(target);
            return;
        }
        try {
            Path path = Path.of(URI.create(target));
            List<String> command = folderCommand(System.getProperty("os.name", ""), path);
            new ProcessBuilder(command).start(); // no se espera: explorer.exe devuelve 1 aunque funcione
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo abrir " + target, e);
        }
    }

    /** El comando que abre {@code path} en el explorador del sistema. */
    static List<String> folderCommand(String osName, Path path) {
        String os = osName.toLowerCase(Locale.ROOT);
        String where = path.toString();
        if (os.contains("win")) {
            // Un archivo se abre seleccionado en su carpeta; una carpeta, por dentro
            return Files.isRegularFile(path) ? List.of("explorer.exe", "/select,", where)
                    : List.of("explorer.exe", where);
        }
        if (os.contains("mac")) {
            return List.of("open", where);
        }
        return List.of("xdg-open", where);
    }
}

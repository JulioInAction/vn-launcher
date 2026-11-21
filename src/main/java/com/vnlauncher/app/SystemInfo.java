package com.vnlauncher.app;

import com.vnlauncher.util.DisplayPaths;

import java.nio.file.Path;

/**
 * «Copiar información del sistema» (Ajustes → Registro de errores) y la cabecera del registro al
 * arrancar: versión de la app, de Java y de JavaFX, sistema operativo, versión del esquema de la
 * base de datos y carpeta de datos.
 *
 * <p>Es una plantilla completa (no trozos pegados), para poder traducirla entera más adelante.
 */
public final class SystemInfo {

    private static final String TEMPLATE = """
            %s %s
            Java: %s (%s)
            JavaFX: %s
            Sistema operativo: %s %s (%s)
            Versión del esquema de la base de datos: %d
            Carpeta de datos: %s""";

    private SystemInfo() {
    }

    /** Con los datos de este ordenador. */
    public static String current(int schemaVersion, Path dataDir) {
        return text(AppInfo.VERSION, System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("javafx.runtime.version", "?"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"), schemaVersion,
                DisplayPaths.forDisplay(dataDir));
    }

    static String text(String appVersion, String javaVersion, String javaVendor, String javafxVersion,
                       String osName, String osVersion, String osArch, int schemaVersion, String dataDir) {
        return String.format(TEMPLATE, AppInfo.NAME, appVersion, javaVersion, javaVendor, javafxVersion,
                osName, osVersion, osArch, schemaVersion, dataDir);
    }
}

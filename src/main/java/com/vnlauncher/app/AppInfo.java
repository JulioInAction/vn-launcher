package com.vnlauncher.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Datos de la aplicación para "Acerca de". */
public final class AppInfo {

    public static final String NAME = "VN Launcher";

    /**
     * Versión del pom.xml. Maven la escribe en app.properties al compilar (ver &lt;resources&gt;
     * en el pom), así solo hay que cambiarla en un sitio.
     */
    public static final String VERSION = loadVersion();

    private AppInfo() {
    }

    private static String loadVersion() {
        try (InputStream in = AppInfo.class.getResourceAsStream("app.properties")) {
            if (in == null) {
                return "?";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "?");
        } catch (IOException e) {
            return "?";
        }
    }
}

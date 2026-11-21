package com.vnlauncher.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Rutas para enseñar o copiar sin el nombre de usuario: "C:\Users\Julio\AppData\Local\VNLauncher"
 * pasa a "%LOCALAPPDATA%\VNLauncher". Así, al pegar los detalles de un error en un informe, no se
 * comparte el nombre de la cuenta de Windows. Fuera de Windows, la carpeta personal se escribe "~".
 */
public final class DisplayPaths {

    /** Variables de Windows, de la más concreta a la más general (LOCALAPPDATA está dentro de USERPROFILE). */
    private static final List<String> WINDOWS_VARIABLES = List.of("LOCALAPPDATA", "APPDATA", "USERPROFILE");

    private DisplayPaths() {
    }

    /** Con las variables de entorno y la carpeta personal de este ordenador. */
    public static String forDisplay(Path path) {
        return forDisplay(path.toAbsolutePath().toString(), System.getenv(), System.getProperty("user.home"));
    }

    /**
     * @param env  variables de entorno (en los tests, un Map hecho a mano)
     * @param home carpeta personal (user.home); puede ser null
     */
    static String forDisplay(String path, Map<String, String> env, String home) {
        for (String variable : WINDOWS_VARIABLES) {
            String value = env.get(variable);
            if (startsWithFolder(path, value)) {
                return "%" + variable + "%" + path.substring(value.length());
            }
        }
        if (startsWithFolder(path, home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }

    /** true si {@code path} es {@code folder} o está dentro (sin confundir C:\Users\Ana con C:\Users\Anabel). */
    private static boolean startsWithFolder(String path, String folder) {
        if (folder == null || folder.isBlank() || !path.regionMatches(true, 0, folder, 0, folder.length())) {
            return false;
        }
        return path.length() == folder.length() || path.charAt(folder.length()) == '\\'
                || path.charAt(folder.length()) == '/';
    }
}

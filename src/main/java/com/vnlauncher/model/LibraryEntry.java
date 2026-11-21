package com.vnlauncher.model;

import java.time.Instant;

/**
 * Una VN que el usuario ha añadido a su biblioteca, con su configuración de lanzamiento.
 *
 * @param exePath           ruta absoluta del ejecutable del juego; null si aún no se ha elegido
 * @param configExePath     ruta absoluta del programa de configuración (config.exe...); null si no hay
 * @param coverOverride     portada elegida en la galería, relativa a la carpeta de datos
 *                          ("covers/v4-cv82903.jpg"); null = la portada principal de VNDB
 * @param useLocaleEmulator si se lanza a través de Locale Emulator (LEProc.exe)
 * @param leProfile         GUID de un perfil concreto de Locale Emulator; null = perfil por defecto
 * @param lastPlayedAt      última vez que se lanzó; null si nunca
 * @param statusOverride    estado fijado a mano desde la ficha; null = automático (ver PlayStatus)
 */
public record LibraryEntry(
        VisualNovel vn,
        Instant addedAt,
        String exePath,
        String configExePath,
        String coverOverride,
        boolean useLocaleEmulator,
        String leProfile,
        Instant lastPlayedAt,
        PlayStatus statusOverride
) {
    public boolean hasExecutable() {
        return exePath != null && !exePath.isBlank();
    }

    public boolean hasConfigExecutable() {
        return configExePath != null && !configExePath.isBlank();
    }
}

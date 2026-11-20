package com.vnlauncher.model;

import java.time.Instant;

/**
 * Una VN que el usuario ha añadido a su biblioteca, con su configuración de lanzamiento.
 *
 * @param exePath           ruta absoluta del ejecutable del juego; null si aún no se ha elegido
 * @param useLocaleEmulator si se lanza a través de Locale Emulator (LEProc.exe)
 * @param leProfile         GUID de un perfil concreto de Locale Emulator; null = perfil por defecto
 * @param lastPlayedAt      última vez que se lanzó; null si nunca
 */
public record LibraryEntry(
        VisualNovel vn,
        Instant addedAt,
        String exePath,
        boolean useLocaleEmulator,
        String leProfile,
        Instant lastPlayedAt
) {
    public boolean hasExecutable() {
        return exePath != null && !exePath.isBlank();
    }
}

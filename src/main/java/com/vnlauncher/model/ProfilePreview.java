package com.vnlauncher.model;

import java.time.LocalDateTime;

/**
 * Un perfil abierto y ya comprobado, a la espera de que el usuario confirme la importación.
 *
 * @param fileName      el nombre del .zip, para el diálogo
 * @param exportedAt    {@link ProfileManifest#exportedAt()} ya leída
 * @param currentNovels las novelas que hay ahora, para «Se sustituirá tu perfil actual (8 novelas)»
 */
public record ProfilePreview(String fileName, ProfileManifest manifest, LocalDateTime exportedAt, int currentNovels) {
}

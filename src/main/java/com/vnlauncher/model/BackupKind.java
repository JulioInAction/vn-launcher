package com.vnlauncher.model;

import java.util.Optional;

/**
 * Por qué se hizo una copia de seguridad. El tipo va en el nombre del archivo
 * (vn-launcher-2026-11-21_104203-semanal.db), así que no hace falta guardarlo en ningún otro sitio.
 */
public enum BackupKind {
    /** Antes de aplicar migraciones nuevas, es decir, al arrancar una versión nueva de la app. */
    BEFORE_UPDATE("actualizacion", "Antes de actualizar a %s"),
    WEEKLY("semanal", "Semanal"),
    BEFORE_IMPORT("antes-de-importar", "Antes de importar un perfil"),
    BEFORE_RESTORE("antes-de-restaurar", "Antes de restaurar una copia");

    private final String slug;
    /** Plantilla completa; solo la de BEFORE_UPDATE lleva hueco (la versión). */
    private final String description;

    BackupKind(String slug, String description) {
        this.slug = slug;
        this.description = description;
    }

    /** Cómo va en el nombre del archivo. */
    public String slug() {
        return slug;
    }

    /** «Antes de actualizar a 1.0.0», «Semanal»... */
    public String describe(String appVersion) {
        return String.format(description, appVersion);
    }

    public static Optional<BackupKind> fromSlug(String slug) {
        for (BackupKind kind : values()) {
            if (kind.slug.equals(slug)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}

package com.vnlauncher.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Una copia de seguridad de la base de datos, en la carpeta backups/.
 *
 * @param createdAt  cuándo se hizo (hora local, la del nombre del archivo)
 * @param appVersion versión a la que se actualizaba (solo en {@link BackupKind#BEFORE_UPDATE}); si no, null
 * @param sizeBytes  tamaño del archivo
 */
public record BackupInfo(Path file, LocalDateTime createdAt, BackupKind kind, String appVersion, long sizeBytes) {

    /** «Antes de actualizar a 1.0.0», «Semanal»... */
    public String description() {
        return kind.describe(appVersion);
    }
}

package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.BackupInfo;
import com.vnlauncher.model.BackupKind;
import com.vnlauncher.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;

/**
 * Restaurar una copia de seguridad. Se hace en dos tiempos, con un reinicio en medio:
 * <ol>
 *   <li>{@link #prepare}, con la app abierta: comprueba la copia, la aparta en pendiente/, hace la
 *       copia «Antes de restaurar una copia» y apunta la operación pendiente;</li>
 *   <li>la app se reinicia y, antes de abrir la base de datos, {@link PendingOperations} la pone en
 *       lugar de library.db.</li>
 * </ol>
 */
public class RestoreService {

    private static final System.Logger LOG = System.getLogger(RestoreService.class.getName());

    static final String DAMAGED = "La copia está dañada o no es una base de datos de VN Launcher, "
            + "así que no se puede restaurar.";
    static final String TOO_NEW = "Esta copia es de una versión más nueva de VN Launcher. "
            + "Actualiza la aplicación para poder restaurarla.";

    private final BackupService backupService;
    private final AppPaths paths;

    public RestoreService(AppPaths paths, BackupService backupService) {
        this.backupService = backupService;
        this.paths = paths;
    }

    /**
     * Comprueba, sin tocarlo, que un archivo se puede restaurar: SQLite lo lee entero sin errores y
     * su esquema no es más nuevo que el de esta versión de la app (las migraciones solo van hacia
     * delante; uno más viejo sí vale, se pone al día al arrancar).
     */
    public void check(Path file) throws InvalidDataFileException {
        Database.FileCheck check = Database.inspect(file);
        if (!check.intact() || check.schemaVersion() <= 0) {
            throw new InvalidDataFileException(DAMAGED);
        }
        if (check.schemaVersion() > Database.latestVersion()) {
            throw new InvalidDataFileException(TOO_NEW);
        }
    }

    /**
     * Deja todo listo para que el próximo arranque ponga esta copia. Hasta que se apunta la
     * operación pendiente (lo último), no ha cambiado nada: si algo falla antes, se deshace.
     */
    public void prepare(BackupInfo backup) throws InvalidDataFileException, IOException, SQLException {
        check(backup.file());
        Path staged = paths.stagedDatabaseFile();
        Path partial = staged.resolveSibling(staged.getFileName() + ".part");
        try {
            PendingOperations.deleteRecursively(paths.pendingDir());
            Files.createDirectories(paths.pendingDir());
            // 1. Apartar la copia elegida ANTES de hacer la nueva: al hacerla se borran las que
            //    sobran (se guardan 5) y la elegida podría ser justo la más vieja
            Files.copy(backup.file(), partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, staged, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            // 2. Cómo está ahora, por si se quiere volver atrás
            backupService.backup(BackupKind.BEFORE_RESTORE);
            // 3. Desde aquí el cambio está decidido: se hará al arrancar
            new PendingOperation(PendingOperation.Kind.RESTORE, backup.createdAt())
                    .write(paths.pendingOperationFile());
        } catch (IOException | SQLException | RuntimeException e) {
            PendingOperations.deleteRecursively(paths.pendingDir());
            throw e;
        }
        LOG.log(System.Logger.Level.INFO, "Restauración preparada: {0}", backup.file().getFileName());
    }
}

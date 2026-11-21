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
import java.util.Optional;

/**
 * Restaurar una copia de seguridad. Se hace en dos tiempos, con un reinicio en medio:
 * <ol>
 *   <li>{@link #prepare}, con la app abierta: comprueba la copia, la aparta como restaurar.db, hace
 *       la copia «Antes de restaurar una copia» y apunta la operación pendiente;</li>
 *   <li>la app se reinicia y, ANTES de abrir la base de datos, {@link #applyPending} pone
 *       restaurar.db en lugar de library.db.</li>
 * </ol>
 * ¿Por qué reiniciar en vez de cambiar la base "en caliente"? Con la app abierta hay conexiones,
 * tareas en segundo plano y pantallas con datos de la base vieja; cambiarla debajo de todo eso es
 * fácil de hacer mal. Al arrancar no hay nada abierto todavía: el cambio es solo mover archivos.
 */
public class RestoreService {

    private static final System.Logger LOG = System.getLogger(RestoreService.class.getName());

    static final String DAMAGED = "La copia está dañada o no es una base de datos de VN Launcher, "
            + "así que no se puede restaurar.";
    static final String TOO_NEW = "Esta copia es de una versión más nueva de VN Launcher. "
            + "Actualiza la aplicación para poder restaurarla.";

    /** Lo que pasó con la operación pendiente al arrancar. */
    public sealed interface Outcome {
        /** Hecho: la base es ya la de la copia. */
        record Done(PendingOperation operation) implements Outcome {
        }

        /**
         * Falló y la base sigue como estaba.
         *
         * @param operation la operación, o null si ni siquiera se pudo leer qué había que hacer
         */
        record Failed(PendingOperation operation, Exception error) implements Outcome {
        }
    }

    private final BackupService backupService;
    private final Path stagedFile;
    private final Path pendingFile;

    public RestoreService(AppPaths paths, BackupService backupService) {
        this.backupService = backupService;
        this.stagedFile = paths.stagedDatabaseFile();
        this.pendingFile = paths.pendingOperationFile();
    }

    /**
     * Comprueba, sin tocarlo, que un archivo se puede restaurar: SQLite lo lee entero sin errores y
     * su esquema no es más nuevo que el de esta versión de la app (las migraciones solo van hacia
     * delante; uno más viejo sí vale, se pone al día al arrancar).
     */
    public void check(Path file) throws InvalidBackupException {
        Database.FileCheck check = Database.inspect(file);
        if (!check.intact() || check.schemaVersion() <= 0) {
            throw new InvalidBackupException(DAMAGED);
        }
        if (check.schemaVersion() > Database.latestVersion()) {
            throw new InvalidBackupException(TOO_NEW);
        }
    }

    /**
     * Deja todo listo para que el próximo arranque ponga esta copia. Hasta que se apunta la
     * operación pendiente (lo último), no ha cambiado nada: si algo falla antes, se deshace.
     */
    public void prepare(BackupInfo backup) throws InvalidBackupException, IOException, SQLException {
        check(backup.file());
        Path partial = stagedFile.resolveSibling(stagedFile.getFileName() + ".part");
        try {
            // 1. Apartar la copia elegida ANTES de hacer la nueva: al hacerla se borran las que
            //    sobran (se guardan 5) y la elegida podría ser justo la más vieja
            Files.copy(backup.file(), partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, stagedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            // 2. Cómo está ahora, por si se quiere volver atrás
            backupService.backup(BackupKind.BEFORE_RESTORE);
            // 3. Desde aquí el cambio está decidido: se hará al arrancar
            new PendingOperation(PendingOperation.Kind.RESTORE, backup.createdAt()).write(pendingFile);
        } catch (IOException | SQLException | RuntimeException e) {
            deleteQuietly(partial);
            deleteQuietly(stagedFile);
            throw e;
        }
        LOG.log(System.Logger.Level.INFO, "Restauración preparada: {0}", backup.file().getFileName());
    }

    /**
     * Al arrancar, antes de abrir la base de datos: si hay una operación pendiente, la termina.
     * Pase lo que pase no se vuelve a intentar en el arranque siguiente: si ha fallado una vez, lo
     * normal es que vuelva a fallar, y la app no llegaría a abrirse nunca.
     *
     * @return vacío si no había nada pendiente
     */
    public static Optional<Outcome> applyPending(AppPaths paths) {
        Path db = paths.databaseFile();
        recoverInterruptedSwap(db);
        Path pending = paths.pendingOperationFile();
        if (!Files.exists(pending)) {
            return Optional.empty();
        }
        PendingOperation operation = null;
        try {
            operation = PendingOperation.read(pending);
            replaceDatabase(paths.stagedDatabaseFile(), db);
            LOG.log(System.Logger.Level.INFO, "Copia de {0} restaurada", operation.dataDate());
            return Optional.of(new Outcome.Done(operation));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "No se pudo terminar la operación pendiente; la base sigue como estaba", e);
            return Optional.of(new Outcome.Failed(operation, e));
        } finally {
            deleteQuietly(pending);
            deleteQuietly(paths.stagedDatabaseFile());
        }
    }

    /**
     * Pone {@code staged} en lugar de {@code db}. La base de antes se aparta (library.db.anterior) y
     * solo se borra cuando la nueva está en su sitio y SQLite la lee bien; si no, vuelve a su sitio.
     */
    static void replaceDatabase(Path staged, Path db) throws IOException {
        if (!Files.isRegularFile(staged)) {
            throw new IOException("Falta la base preparada " + staged.getFileName());
        }
        Path wal = sibling(db, "-wal");
        Path old = sibling(db, ".anterior");
        Path oldWal = sibling(db, ".anterior-wal");
        Files.deleteIfExists(old);  // restos de un cambio anterior que ya terminó bien
        Files.deleteIfExists(oldWal);

        boolean hadDb = Files.exists(db);
        boolean hadWal = Files.exists(wal);
        if (hadDb) {
            Files.move(db, old);
        }
        // El -wal tiene cambios de la base VIEJA: si se quedara, SQLite los aplicaría sobre la nueva
        // y la estropearía. Se aparta con ella. El -shm es solo un índice del -wal: se borra.
        if (hadWal) {
            Files.move(wal, oldWal);
        }
        Files.deleteIfExists(sibling(db, "-shm"));
        try {
            Files.move(staged, db, StandardCopyOption.ATOMIC_MOVE);
            if (!Database.inspect(db).intact()) {
                throw new IOException("La base restaurada no se puede leer");
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(db);
                if (hadDb) {
                    Files.move(old, db);
                }
                if (hadWal) {
                    Files.move(oldWal, wal);
                }
            } catch (IOException rollback) {
                // Si ni esto se puede, recoverInterruptedSwap lo vuelve a intentar al arrancar
                e.addSuppressed(rollback);
            }
            throw e;
        }
        deleteQuietly(old);
        deleteQuietly(oldWal);
    }

    /**
     * Si un cambio se cortó a medias (apagón, o falló la vuelta atrás) y no hay library.db pero sí
     * library.db.anterior, se vuelve a poner la de antes. Así nunca se arranca con una base vacía
     * teniendo los datos al lado.
     */
    static void recoverInterruptedSwap(Path db) {
        Path old = sibling(db, ".anterior");
        if (Files.exists(db) || !Files.exists(old)) {
            return;
        }
        try {
            Files.move(old, db);
            Path oldWal = sibling(db, ".anterior-wal");
            if (Files.exists(oldWal)) {
                Files.move(oldWal, sibling(db, "-wal"), StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.log(System.Logger.Level.WARNING, "Un cambio de base de datos quedó a medias: se ha recuperado la anterior");
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "No se pudo recuperar " + old.getFileName(), e);
        }
    }

    private static Path sibling(Path file, String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo borrar " + file, e);
        }
    }
}

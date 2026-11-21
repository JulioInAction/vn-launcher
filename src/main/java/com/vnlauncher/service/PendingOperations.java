package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Termina, al arrancar y ANTES de abrir la base de datos, lo que se dejó preparado con la app
 * abierta: restaurar una copia ({@link RestoreService}) o importar un perfil ({@link ProfileService}).
 *
 * <p>¿Por qué al arrancar y no "en caliente"? Con la app abierta hay conexiones, tareas en segundo
 * plano y pantallas con datos de la base vieja; cambiarla debajo de todo eso es fácil de hacer mal.
 * Al arrancar no hay nada abierto todavía: el cambio es solo mover archivos.
 *
 * <p>Los ajustes que son de este ordenador no se cambian (ver {@link #keepLocalSettings}).
 */
public final class PendingOperations {

    private static final System.Logger LOG = System.getLogger(PendingOperations.class.getName());

    /** Lo que pasó con la operación pendiente al arrancar. */
    public sealed interface Outcome {
        /** Hecho: la base es ya la de la copia o la del perfil. */
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

    /** Los ajustes de este ordenador, leídos de la base de antes del cambio. */
    record LocalSettings(Optional<String> window, Optional<Path> localeEmulator) {
        static final LocalSettings NONE = new LocalSettings(Optional.empty(), Optional.empty());
    }

    private PendingOperations() {
    }

    /**
     * Si hay una operación pendiente, la termina. Pase lo que pase no se vuelve a intentar en el
     * arranque siguiente: si ha fallado una vez, lo normal es que vuelva a fallar, y la app no
     * llegaría a abrirse nunca.
     *
     * @return vacío si no había nada pendiente
     */
    public static Optional<Outcome> applyPending(AppPaths paths) {
        Path db = paths.databaseFile();
        recoverInterruptedSwap(db);
        Path marker = paths.pendingOperationFile();
        if (!Files.exists(marker)) {
            // Restos de un perfil que se abrió para verlo y no se llegó a importar (la app se cerró)
            deleteRecursively(paths.pendingDir());
            return Optional.empty();
        }
        PendingOperation operation = null;
        try {
            operation = PendingOperation.read(marker);
            LocalSettings local = readLocalSettings(db);
            replaceDatabase(paths.stagedDatabaseFile(), db);
            // Desde aquí la base nueva ya está en su sitio: lo que falle ya no la deshace
            keepLocalSettings(db, local, Files::exists);
            if (operation.kind() == PendingOperation.Kind.IMPORT) {
                mergeCovers(paths.stagedCoversDir(), paths.coversDir());
            }
            LOG.log(System.Logger.Level.INFO, "Operación pendiente terminada: {0} ({1})",
                    operation.kind(), operation.dataDate());
            return Optional.of(new Outcome.Done(operation));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "No se pudo terminar la operación pendiente; la base sigue como estaba", e);
            return Optional.of(new Outcome.Failed(operation, e));
        } finally {
            deleteQuietly(marker);
            deleteRecursively(paths.pendingDir());
        }
    }

    // ====================================================================== cambiar la base

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
                throw new IOException("La base nueva no se puede leer");
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

    // ====================================================================== ajustes de este ordenador

    /** Lee de la base actual (antes de cambiarla) los ajustes que son de este ordenador. */
    static LocalSettings readLocalSettings(Path db) {
        if (!Files.exists(db)) {
            return LocalSettings.NONE; // (abrirla la crearía vacía)
        }
        try {
            SettingsRepository settings = new SettingsRepository(new Database(db));
            return new LocalSettings(settings.get(SettingsService.WINDOW),
                    settings.get(SettingsService.LOCALE_EMULATOR_PATH).filter(s -> !s.isBlank()).map(Path::of));
        } catch (SQLException | RuntimeException e) {
            // Una base que no se puede leer no tiene ajustes que conservar: se quedan los nuevos
            LOG.log(System.Logger.Level.WARNING, "No se pudieron leer los ajustes de este ordenador", e);
            return LocalSettings.NONE;
        }
    }

    /**
     * Deja en la base nueva los ajustes que son de este ordenador:
     * <ul>
     *   <li>la posición y el tamaño de la ventana, siempre los de aquí (otra pantalla, otro tamaño);</li>
     *   <li>la ruta de LEProc.exe: ver {@link #chooseLocaleEmulator}.</li>
     * </ul>
     * El resto (tema, spoilers, copias semanales...) viene con los datos nuevos. Si falla, solo se
     * registra: la base nueva ya está puesta y es lo importante.
     */
    static void keepLocalSettings(Path db, LocalSettings local, Predicate<Path> exists) {
        try {
            SettingsRepository settings = new SettingsRepository(new Database(db));
            if (local.window().isPresent()) {
                settings.put(SettingsService.WINDOW, local.window().get());
            } else {
                settings.remove(SettingsService.WINDOW); // la primera vez aquí: grande y centrada
            }
            Optional<Path> incoming = settings.get(SettingsService.LOCALE_EMULATOR_PATH)
                    .filter(s -> !s.isBlank()).map(Path::of);
            Optional<Path> chosen = chooseLocaleEmulator(incoming, local.localeEmulator(), exists);
            if (!chosen.equals(incoming)) {
                settings.put(SettingsService.LOCALE_EMULATOR_PATH, chosen.orElseThrow().toString());
            }
        } catch (SQLException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudieron conservar los ajustes de este ordenador", e);
        }
    }

    /**
     * La ruta de LEProc.exe tras el cambio: la que traen los datos nuevos si existe en este
     * ordenador; si no existe y la de aquí sí, la de aquí; si tampoco, la que traen (se verá
     * «No encontrado» en Ajustes y se podrá corregir).
     */
    static Optional<Path> chooseLocaleEmulator(Optional<Path> incoming, Optional<Path> local,
                                               Predicate<Path> exists) {
        if (incoming.filter(exists).isPresent()) {
            return incoming;
        }
        return local.filter(exists).isPresent() ? local : incoming;
    }

    // ====================================================================== portadas

    /**
     * Suma las portadas del perfil a las de covers/. No se borra ni se cambia ninguna de las que ya
     * hay: si coincide el nombre, es la misma imagen (los nombres llevan la VN y el id de la imagen
     * de VNDB, o la hora exacta en que se eligió el archivo).
     */
    static void mergeCovers(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        Files.createDirectories(to);
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.toList()) {
                Path target = to.resolve(file.getFileName().toString());
                if (!Files.exists(target)) {
                    Files.move(file, target);
                }
            }
        }
    }

    // ====================================================================== utilidades

    private static Path sibling(Path file, String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }

    static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo borrar " + file, e);
        }
    }

    /** Borra una carpeta con todo lo que tenga (primero lo de dentro). Si no existe, nada. */
    static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> all = Files.walk(dir)) {
            // Orden inverso: los archivos antes que su carpeta
            all.sorted(Comparator.reverseOrder()).forEach(PendingOperations::deleteQuietly);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo borrar " + dir, e);
        }
    }
}

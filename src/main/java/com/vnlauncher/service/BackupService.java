package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.BackupInfo;
import com.vnlauncher.model.BackupKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Copias de seguridad automáticas de la base de datos, en la carpeta backups/:
 * <ul>
 *   <li>antes de aplicar migraciones nuevas (al arrancar una versión nueva de la app);</li>
 *   <li>una cada semana (se puede desactivar en Ajustes);</li>
 *   <li>antes de restaurar una copia (ver RestoreService) y de importar un perfil (fase siguiente).</li>
 * </ul>
 * Se guardan las {@link #KEEP} últimas, de todos los tipos juntos.
 *
 * <p>Solo se copia la base de datos: las imágenes de la caché se pueden volver a descargar y las
 * portadas propias (covers/) nunca se borran al importar.
 *
 * <p>Todo lo que hay que saber de cada copia va en el nombre del archivo:
 * {@code vn-launcher-2026-11-21_104203-actualizacion-1.0.0.db} (fecha y hora, tipo y, si es una
 * actualización, la versión). Así no hace falta ningún índice que se pueda desincronizar.
 */
public class BackupService {

    private static final System.Logger LOG = System.getLogger(BackupService.class.getName());

    static final int KEEP = 5;
    static final Duration WEEKLY_EVERY = Duration.ofDays(7);

    private static final String PREFIX = "vn-launcher-";
    private static final String EXTENSION = ".db";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final int STAMP_LENGTH = "2026-11-21_104203".length();
    private static final String UPDATE_PREFIX = BackupKind.BEFORE_UPDATE.slug() + "-";

    private final Database database;
    private final Path dir;
    private final Clock clock;
    private final String appVersion;

    /** @param appVersion la versión de la app, para «Antes de actualizar a 1.0.0» */
    public BackupService(Database database, Path dir, Clock clock, String appVersion) {
        this.database = database;
        this.dir = dir;
        this.clock = clock;
        this.appVersion = appVersion;
    }

    public Path dir() {
        return dir;
    }

    /**
     * Hace una copia ahora y borra las que sobren. Se escribe primero con otro nombre (.part) y se
     * renombra al terminar: una copia a medias (disco lleno, app cerrada) nunca parece buena.
     */
    public BackupInfo backup(BackupKind kind) throws SQLException, IOException {
        Files.createDirectories(dir);
        LocalDateTime now = LocalDateTime.now(clock);
        String name = PREFIX + STAMP.format(now) + "-"
                + (kind == BackupKind.BEFORE_UPDATE ? UPDATE_PREFIX + appVersion : kind.slug()) + EXTENSION;
        Path target = dir.resolve(name);
        Path partial = dir.resolve(name + ".part");
        Files.deleteIfExists(partial);
        database.backupTo(partial);
        Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        LOG.log(System.Logger.Level.INFO, "Copia de seguridad: {0}", name);
        prune();
        return parse(target).orElseThrow();
    }

    /** Si la app se acaba de actualizar (hay migraciones pendientes), copia antes de migrar. */
    public Optional<BackupInfo> beforeMigration() throws SQLException, IOException {
        return database.needsMigration() ? Optional.of(backup(BackupKind.BEFORE_UPDATE)) : Optional.empty();
    }

    /**
     * La copia semanal, si toca: está activada y la última semanal tiene más de 7 días (o no hay).
     *
     * @return la copia hecha, o vacío si no tocaba
     */
    public Optional<BackupInfo> weeklyIfDue(boolean enabled) throws SQLException, IOException {
        return enabled && weeklyDue() ? Optional.of(backup(BackupKind.WEEKLY)) : Optional.empty();
    }

    boolean weeklyDue() throws IOException {
        Optional<LocalDateTime> last = list().stream()
                .filter(b -> b.kind() == BackupKind.WEEKLY)
                .map(BackupInfo::createdAt)
                .max(Comparator.naturalOrder());
        LocalDateTime limit = LocalDateTime.now(clock).minus(WEEKLY_EVERY);
        return last.isEmpty() || !last.get().isAfter(limit);
    }

    /** Las copias que hay, de la más nueva a la más vieja. Los archivos que no son copias se ignoran. */
    public List<BackupInfo> list() throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BackupInfo> backups = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(file -> parse(file).ifPresent(backups::add));
        }
        backups.sort(Comparator.comparing(BackupInfo::createdAt)
                .thenComparing(b -> b.file().getFileName().toString())
                .reversed());
        return backups;
    }

    /** Deja las KEEP más nuevas. Solo toca archivos con nombre de copia (nunca otros de la carpeta). */
    private void prune() throws IOException {
        List<BackupInfo> backups = list();
        for (BackupInfo old : backups.subList(Math.min(KEEP, backups.size()), backups.size())) {
            Files.deleteIfExists(old.file());
            LOG.log(System.Logger.Level.INFO, "Copia antigua borrada: {0}", old.file().getFileName());
        }
    }

    /** Lee fecha, tipo y versión del nombre del archivo; vacío si no es una copia nuestra. */
    static Optional<BackupInfo> parse(Path file) {
        String name = file.getFileName().toString();
        if (!name.startsWith(PREFIX) || !name.endsWith(EXTENSION)
                || name.length() < PREFIX.length() + STAMP_LENGTH + 2 + EXTENSION.length()) {
            return Optional.empty();
        }
        String rest = name.substring(PREFIX.length(), name.length() - EXTENSION.length());
        String stamp = rest.substring(0, STAMP_LENGTH);
        if (rest.charAt(STAMP_LENGTH) != '-') {
            return Optional.empty();
        }
        String kindPart = rest.substring(STAMP_LENGTH + 1);
        LocalDateTime createdAt;
        try {
            createdAt = LocalDateTime.parse(stamp, STAMP);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        BackupKind kind;
        String version = null;
        if (kindPart.startsWith(UPDATE_PREFIX)) {
            kind = BackupKind.BEFORE_UPDATE;
            version = kindPart.substring(UPDATE_PREFIX.length());
        } else {
            Optional<BackupKind> other = BackupKind.fromSlug(kindPart);
            if (other.isEmpty()) {
                return Optional.empty();
            }
            kind = other.get();
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return Optional.empty(); // se ha borrado mientras tanto
        }
        return Optional.of(new BackupInfo(file, createdAt, kind, version, size));
    }
}

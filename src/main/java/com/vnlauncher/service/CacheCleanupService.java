package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.VnScreenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Limpia la caché de VNDB de lo que ya no se necesita.
 *
 * <p>«Ver ficha» en la búsqueda guarda la VN (con sus etiquetas, capturas y personajes) aunque no se
 * añada a la biblioteca. Esas VNs sueltas se borran cuando llevan {@link #MAX_AGE} sin verse
 * ({@code vn.last_viewed_at}). Con ellas se van sus etiquetas, capturas y enlaces a desarrolladores y
 * personajes (ON DELETE CASCADE), los personajes y desarrolladores que se quedan sin ninguna VN y las
 * imágenes descargadas de todo ello.
 *
 * <p>Nunca toca:
 * <ul>
 *   <li>las VNs de la biblioteca (ni lo suyo);</li>
 *   <li>un personaje con ruta, aunque ninguna VN lo use ya.</li>
 * </ul>
 */
public class CacheCleanupService {

    private static final System.Logger LOG = System.getLogger(CacheCleanupService.class.getName());

    /** Cuánto se guarda una VN que no está en la biblioteca desde la última vez que se vio. */
    public static final Duration MAX_AGE = Duration.ofDays(30);

    /** Lo que se ha borrado. */
    public record Result(int visualNovels, int characters) {
        public boolean isEmpty() {
            return visualNovels == 0 && characters == 0;
        }
    }

    private final Database db;
    private final ImageCacheService images;
    private final Clock clock;

    public CacheCleanupService(Database db, ImageCacheService images, Clock clock) {
        this.db = db;
        this.images = images;
        this.clock = clock;
    }

    /** Hace la limpieza. Llamar en segundo plano: toca la base de datos y borra archivos. */
    public Result cleanUp() throws SQLException {
        long cutoff = clock.instant().minus(MAX_AGE).getEpochSecond();
        List<String> files = new ArrayList<>(); // se borran DESPUÉS de confirmar la transacción
        int[] counts = new int[2];

        db.inTransaction(conn -> {
            List<String> staleIds = staleVnIds(conn, cutoff);
            for (String id : staleIds) {
                collectVnFiles(conn, id, files);
            }
            for (String id : staleIds) {
                delete(conn, "DELETE FROM vn WHERE id = ?", id); // arrastra tags, capturas y enlaces
            }
            counts[0] = staleIds.size();
            counts[1] = deleteOrphanCharacters(conn, files);
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE FROM developer WHERE id NOT IN (SELECT developer_id FROM vn_developer)
                    """)) {
                ps.executeUpdate();
            }
        });

        files.forEach(this::deleteQuietly);
        Result result = new Result(counts[0], counts[1]);
        if (!result.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "Caché limpiada: {0} novelas y {1} personajes",
                    result.visualNovels(), result.characters());
        }
        return result;
    }

    /** VNs fuera de la biblioteca que no se ven desde hace más de MAX_AGE (sin fecha, cuenta cuándo se pidió). */
    private static List<String> staleVnIds(Connection conn, long cutoff) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id FROM vn
                WHERE id NOT IN (SELECT vn_id FROM library_entry)
                  AND COALESCE(last_viewed_at, fetched_at) < ?
                """)) {
            ps.setLong(1, cutoff);
            return strings(ps);
        }
    }

    /** Portada, y capturas guardadas en disco, de una VN que se va a borrar. */
    private static void collectVnFiles(Connection conn, String vnId, List<String> files) throws SQLException {
        try (PreparedStatement cover = conn.prepareStatement("SELECT image_path FROM vn WHERE id = ?")) {
            cover.setString(1, vnId);
            files.addAll(strings(cover));
        }
        try (PreparedStatement shots = conn.prepareStatement(
                "SELECT url, thumbnail_url FROM vn_screenshot WHERE vn_id = ?")) {
            shots.setString(1, vnId);
            try (ResultSet rs = shots.executeQuery()) {
                while (rs.next()) {
                    files.addAll(ScreenshotService.relativePathsOf(
                            new VnScreenshot(rs.getString(1), rs.getString(2), null, null)));
                }
            }
        }
    }

    /**
     * Personajes que ya no salen en ninguna VN y no tienen ruta. Se mira después de borrar las VNs, y
     * con la base entera: si algún personaje quedó suelto por otro motivo, también se limpia.
     */
    private static int deleteOrphanCharacters(Connection conn, List<String> files) throws SQLException {
        String orphans = """
                FROM character
                WHERE id NOT IN (SELECT character_id FROM vn_character)
                  AND id NOT IN (SELECT character_id FROM route WHERE character_id IS NOT NULL)
                """;
        try (PreparedStatement select = conn.prepareStatement("SELECT image_path " + orphans)) {
            files.addAll(strings(select));
        }
        try (PreparedStatement delete = conn.prepareStatement("DELETE " + orphans)) {
            return delete.executeUpdate();
        }
    }

    private static void delete(Connection conn, String sql, String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    /** La primera columna de cada fila, sin los NULL. */
    private static List<String> strings(PreparedStatement ps) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    /** Ya está borrado de la base de datos: si un archivo no se puede quitar, solo se apunta en el registro. */
    private void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(images.resolve(relativePath));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo borrar " + relativePath, e);
        }
    }
}

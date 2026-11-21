package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.VnDetails;
import com.vnlauncher.model.VnScreenshot;
import com.vnlauncher.model.VnTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Etiquetas y capturas de cada VN: tablas vn_tag y vn_screenshot, y el control de caché
 * vn.details_fetched_at (misma idea que los personajes en {@link CharacterRepository}).
 */
public class VnDetailsRepository {

    private final Database db;

    public VnDetailsRepository(Database db) {
        this.db = db;
    }

    /**
     * Sustituye las etiquetas y capturas de una VN por estas y la marca como "ya pedidas", todo en
     * una transacción: si algo falla a medias, no queda la marca puesta con los datos incompletos.
     */
    public void saveForVn(String vnId, VnDetails details, Instant fetchedAt) throws SQLException {
        db.inTransaction(conn -> {
            try (PreparedStatement deleteTags = conn.prepareStatement("DELETE FROM vn_tag WHERE vn_id = ?");
                 PreparedStatement deleteShots = conn.prepareStatement("DELETE FROM vn_screenshot WHERE vn_id = ?");
                 PreparedStatement insertTag = conn.prepareStatement("""
                         INSERT INTO vn_tag (vn_id, tag_id, name, rating, spoiler, category)
                         VALUES (?, ?, ?, ?, ?, ?)
                         """);
                 PreparedStatement insertShot = conn.prepareStatement("""
                         INSERT INTO vn_screenshot (vn_id, position, url, thumbnail_url, sexual, violence)
                         VALUES (?, ?, ?, ?, ?, ?)
                         """);
                 PreparedStatement markFetched = conn.prepareStatement(
                         "UPDATE vn SET details_fetched_at = ? WHERE id = ?")) {

                deleteTags.setString(1, vnId);
                deleteTags.executeUpdate();
                deleteShots.setString(1, vnId);
                deleteShots.executeUpdate();

                for (VnTag tag : details.tags()) {
                    insertTag.setString(1, vnId);
                    insertTag.setString(2, tag.id());
                    insertTag.setString(3, tag.name());
                    insertTag.setDouble(4, tag.rating());
                    insertTag.setInt(5, tag.spoiler());
                    insertTag.setString(6, tag.category());
                    insertTag.addBatch();
                }
                insertTag.executeBatch();

                int position = 0;
                for (VnScreenshot shot : details.screenshots()) {
                    insertShot.setString(1, vnId);
                    insertShot.setInt(2, position++);
                    insertShot.setString(3, shot.url());
                    insertShot.setString(4, shot.thumbnailUrl());
                    VnRows.setNullableDouble(insertShot, 5, shot.sexual());
                    VnRows.setNullableDouble(insertShot, 6, shot.violence());
                    insertShot.addBatch();
                }
                insertShot.executeBatch();

                markFetched.setLong(1, fetchedAt.getEpochSecond());
                markFetched.setString(2, vnId);
                markFetched.executeUpdate();
            }
        });
    }

    /** true si las etiquetas y capturas de esta VN ya se pidieron a VNDB alguna vez. */
    public boolean isFetched(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT details_fetched_at IS NOT NULL FROM vn WHERE id = ?")) {
            ps.setString(1, vnId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Etiquetas (de mayor a menor puntuación) y capturas (en su orden). A igual puntuación, las
     * etiquetas salen en el orden en que se guardaron, que es el de VNDB: rowid es el número de fila
     * interno de SQLite y crece con cada INSERT.
     */
    public VnDetails findByVn(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement tagsQuery = conn.prepareStatement("""
                     SELECT tag_id, name, rating, spoiler, category FROM vn_tag
                     WHERE vn_id = ?
                     ORDER BY rating DESC, rowid
                     """);
             PreparedStatement shotsQuery = conn.prepareStatement("""
                     SELECT url, thumbnail_url, sexual, violence FROM vn_screenshot
                     WHERE vn_id = ?
                     ORDER BY position
                     """)) {
            tagsQuery.setString(1, vnId);
            List<VnTag> tags = new ArrayList<>();
            try (ResultSet rs = tagsQuery.executeQuery()) {
                while (rs.next()) {
                    tags.add(new VnTag(rs.getString("tag_id"), rs.getString("name"), rs.getDouble("rating"),
                            rs.getInt("spoiler"), rs.getString("category")));
                }
            }
            shotsQuery.setString(1, vnId);
            List<VnScreenshot> screenshots = new ArrayList<>();
            try (ResultSet rs = shotsQuery.executeQuery()) {
                while (rs.next()) {
                    screenshots.add(new VnScreenshot(rs.getString("url"), rs.getString("thumbnail_url"),
                            VnRows.getNullableDouble(rs, "sexual"), VnRows.getNullableDouble(rs, "violence")));
                }
            }
            return new VnDetails(tags, screenshots);
        }
    }

    /** Ids de las VNs que tienen esta etiqueta (para filtrar la biblioteca). */
    public Set<String> vnIdsWithTag(String tagId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT vn_id FROM vn_tag WHERE tag_id = ?")) {
            ps.setString(1, tagId);
            Set<String> ids = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
            return ids;
        }
    }

    /**
     * Cuántas VNs de la biblioteca aún no tienen sus etiquetas guardadas (nunca se ha abierto su
     * ficha): el filtro por etiqueta no puede saber si las tienen.
     */
    public int countLibraryWithoutDetails() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT COUNT(*) FROM library_entry le
                     JOIN vn ON vn.id = le.vn_id
                     WHERE vn.details_fetched_at IS NULL
                     """);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}

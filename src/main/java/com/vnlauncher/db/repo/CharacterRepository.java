package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.CharacterRole;
import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.VnCharacter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Acceso a los personajes: tablas character y vn_character, y el control de caché vn.characters_fetched_at. */
public class CharacterRepository {

    private final Database db;

    public CharacterRepository(Database db) {
        this.db = db;
    }

    /**
     * Guarda los personajes de una VN y la marca como "personajes ya descargados", todo en una
     * transacción. Si se cortara a medias, la VN seguiría sin marcar y se volverían a pedir:
     * nunca quedamos con la marca puesta y los personajes a medias.
     *
     * <p>Los enlaces vn_character de esta VN se reemplazan enteros (borrar + insertar).
     * Los personajes en sí no se borran: podrían estar en otra VN o tener rutas (Fase 4).
     */
    public void saveForVn(String vnId, List<VnCharacter> characters, Instant fetchedAt) throws SQLException {
        db.inTransaction(conn -> {
            try (PreparedStatement upsert = conn.prepareStatement("""
                         INSERT INTO character (id, name, original, description,
                                                image_url, image_sexual, image_violence, fetched_at)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                         ON CONFLICT(id) DO UPDATE SET
                             name           = excluded.name,
                             original       = excluded.original,
                             description    = excluded.description,
                             image_path     = CASE WHEN character.image_url IS excluded.image_url
                                                   THEN character.image_path ELSE NULL END,
                             image_url      = excluded.image_url,
                             image_sexual   = excluded.image_sexual,
                             image_violence = excluded.image_violence,
                             fetched_at     = excluded.fetched_at
                         """);
                 PreparedStatement deleteLinks = conn.prepareStatement(
                         "DELETE FROM vn_character WHERE vn_id = ?");
                 PreparedStatement insertLink = conn.prepareStatement(
                         "INSERT INTO vn_character (vn_id, character_id, role, spoiler) VALUES (?, ?, ?, ?)");
                 PreparedStatement markFetched = conn.prepareStatement(
                         "UPDATE vn SET characters_fetched_at = ? WHERE id = ?")) {

                deleteLinks.setString(1, vnId);
                deleteLinks.executeUpdate();

                for (VnCharacter vc : characters) {
                    GameCharacter c = vc.character();
                    upsert.setString(1, c.id());
                    upsert.setString(2, c.name());
                    upsert.setString(3, c.original());
                    upsert.setString(4, c.description());
                    upsert.setString(5, c.imageUrl());
                    VnRows.setNullableDouble(upsert, 6, c.imageSexual());
                    VnRows.setNullableDouble(upsert, 7, c.imageViolence());
                    upsert.setLong(8, fetchedAt.getEpochSecond());
                    // addBatch: acumula la sentencia y la manda junto con las demás al final
                    // (executeBatch). Con muchos personajes es más rápido que ejecutar una a una.
                    upsert.addBatch();

                    insertLink.setString(1, vnId);
                    insertLink.setString(2, c.id());
                    insertLink.setString(3, vc.role().apiValue());
                    insertLink.setInt(4, vc.spoilerLevel());
                    insertLink.addBatch();
                }
                // Primero los personajes y luego los enlaces: vn_character tiene una FK a character.
                upsert.executeBatch();
                insertLink.executeBatch();

                markFetched.setLong(1, fetchedAt.getEpochSecond());
                markFetched.setString(2, vnId);
                markFetched.executeUpdate();
            }
        });
    }

    /** true si los personajes de esta VN ya se pidieron a VNDB alguna vez. */
    public boolean isFetched(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT characters_fetched_at IS NOT NULL FROM vn WHERE id = ?")) {
            ps.setString(1, vnId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Personajes guardados de una VN: primero los que no son spoiler, luego por nombre.
     * (En SQLite una comparación como "spoiler > 0" vale 0 o 1, así que se puede ordenar por ella.)
     */
    public List<VnCharacter> findByVn(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT c.id, c.name, c.original, c.description, c.image_url, c.image_path,
                            c.image_sexual, c.image_violence, vc.role, vc.spoiler
                     FROM vn_character vc
                     JOIN character c ON c.id = vc.character_id
                     WHERE vc.vn_id = ?
                     ORDER BY vc.spoiler > 0, c.name COLLATE NOCASE
                     """)) {
            ps.setString(1, vnId);
            List<VnCharacter> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GameCharacter c = new GameCharacter(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("original"),
                            rs.getString("description"),
                            rs.getString("image_url"),
                            rs.getString("image_path"),
                            VnRows.getNullableDouble(rs, "image_sexual"),
                            VnRows.getNullableDouble(rs, "image_violence"));
                    result.add(new VnCharacter(c,
                            CharacterRole.fromApi(rs.getString("role")), rs.getInt("spoiler")));
                }
            }
            return result;
        }
    }

    public void updateImagePath(String characterId, String imagePath) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE character SET image_path = ? WHERE id = ?")) {
            ps.setString(1, imagePath);
            ps.setString(2, characterId);
            ps.executeUpdate();
        }
    }
}

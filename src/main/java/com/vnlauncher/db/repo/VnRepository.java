package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.Developer;
import com.vnlauncher.model.VisualNovel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Acceso a la caché de VNs de VNDB: tablas vn, developer y vn_developer. */
public class VnRepository {

    private final Database db;

    public VnRepository(Database db) {
        this.db = db;
    }

    /**
     * Guarda la VN y sus desarrolladores (inserta o actualiza), todo en una transacción.
     *
     * <p>Usa "UPSERT" de SQLite: {@code INSERT ... ON CONFLICT(id) DO UPDATE}. Si la VN no existe
     * se inserta; si ya existe, se actualizan sus columnas con los valores nuevos ("excluded" es la
     * fila que intentábamos insertar). Así no hay que consultar antes si existe.
     *
     * <p>Hay columnas que el UPDATE NO toca a propósito: characters_fetched_at (es nuestro control
     * de caché). Y image_path solo se conserva si la URL de la portada no ha cambiado: si VNDB
     * cambió la portada, la ruta local ya no vale y se pone a NULL para que se descargue la nueva.
     */
    public void save(VisualNovel vn, Instant fetchedAt) throws SQLException {
        db.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO vn (id, title, alt_title, description, released,
                                    image_url, image_sexual, image_violence, fetched_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        title          = excluded.title,
                        alt_title      = excluded.alt_title,
                        description    = excluded.description,
                        released       = excluded.released,
                        image_path     = CASE WHEN vn.image_url IS excluded.image_url
                                              THEN vn.image_path ELSE NULL END,
                        image_url      = excluded.image_url,
                        image_sexual   = excluded.image_sexual,
                        image_violence = excluded.image_violence,
                        fetched_at     = excluded.fetched_at
                    """)) {
                // "IS" en vez de "=" en el CASE: en SQL, NULL = NULL no es verdadero, pero NULL IS NULL sí.
                ps.setString(1, vn.id());
                ps.setString(2, vn.title());
                ps.setString(3, vn.altTitle());
                ps.setString(4, vn.description());
                ps.setString(5, vn.released());
                ps.setString(6, vn.imageUrl());
                VnRows.setNullableDouble(ps, 7, vn.imageSexual());
                VnRows.setNullableDouble(ps, 8, vn.imageViolence());
                ps.setLong(9, fetchedAt.getEpochSecond());
                ps.executeUpdate();
            }
            saveDevelopers(conn, vn);
        });
    }

    private void saveDevelopers(Connection conn, VisualNovel vn) throws SQLException {
        try (PreparedStatement upsertDev = conn.prepareStatement("""
                     INSERT INTO developer (id, name, original) VALUES (?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET name = excluded.name, original = excluded.original
                     """);
             PreparedStatement deleteLinks = conn.prepareStatement("DELETE FROM vn_developer WHERE vn_id = ?");
             PreparedStatement insertLink = conn.prepareStatement(
                     "INSERT INTO vn_developer (vn_id, developer_id) VALUES (?, ?)")) {

            // Borrar y volver a crear los enlaces es más simple que calcular cuáles sobran o faltan.
            deleteLinks.setString(1, vn.id());
            deleteLinks.executeUpdate();

            for (Developer dev : vn.developers()) {
                upsertDev.setString(1, dev.id());
                upsertDev.setString(2, dev.name());
                upsertDev.setString(3, dev.original());
                upsertDev.executeUpdate();

                insertLink.setString(1, vn.id());
                insertLink.setString(2, dev.id());
                insertLink.executeUpdate();
            }
        }
    }

    public Optional<VisualNovel> findById(String id) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + VnRows.COLUMNS + " FROM vn v WHERE v.id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                List<Developer> devs = VnRows.loadDevelopers(conn, "vd.vn_id = ?", id)
                        .getOrDefault(id, List.of());
                return Optional.of(VnRows.map(rs, devs));
            }
        }
    }

    /** Apunta dónde se ha guardado la portada (ruta relativa a la carpeta de datos). */
    public void updateImagePath(String id, String imagePath) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE vn SET image_path = ? WHERE id = ?")) {
            ps.setString(1, imagePath);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }
}

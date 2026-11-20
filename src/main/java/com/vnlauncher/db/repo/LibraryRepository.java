package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.Developer;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acceso a la biblioteca del usuario: tabla library_entry (unida con vn para leer). */
public class LibraryRepository {

    /** SELECT común: datos de la VN + fecha en que se añadió. */
    private static final String SELECT_ENTRIES =
            "SELECT " + VnRows.COLUMNS + ", l.added_at "
                    + "FROM library_entry l JOIN vn v ON v.id = l.vn_id ";

    private final Database db;

    public LibraryRepository(Database db) {
        this.db = db;
    }

    /**
     * Añade una VN (que ya debe estar en la tabla vn). Si ya estaba, no hace nada:
     * "ON CONFLICT DO NOTHING" evita el error de clave duplicada y conserva la fecha original.
     */
    public void add(String vnId, Instant addedAt) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO library_entry (vn_id, added_at) VALUES (?, ?) ON CONFLICT(vn_id) DO NOTHING")) {
            ps.setString(1, vnId);
            ps.setLong(2, addedAt.getEpochSecond());
            ps.executeUpdate();
        }
    }

    /** Quita la VN de la biblioteca. Sus rutas y sesiones se borran en cascada (ver esquema). */
    public void remove(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM library_entry WHERE vn_id = ?")) {
            ps.setString(1, vnId);
            ps.executeUpdate();
        }
    }

    public boolean contains(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM library_entry WHERE vn_id = ?")) {
            ps.setString(1, vnId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Toda la biblioteca ordenada por título (COLLATE NOCASE = sin distinguir mayúsculas). */
    public List<LibraryEntry> findAll() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SELECT_ENTRIES + "ORDER BY v.title COLLATE NOCASE")) {
            // Desarrolladores de TODAS las VNs de la biblioteca en una sola consulta (ver VnRows).
            Map<String, List<Developer>> devs = VnRows.loadDevelopers(conn,
                    "vd.vn_id IN (SELECT vn_id FROM library_entry)");
            List<LibraryEntry> entries = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapEntry(rs, devs.getOrDefault(rs.getString("id"), List.of())));
                }
            }
            return entries;
        }
    }

    public Optional<LibraryEntry> findByVnId(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ENTRIES + "WHERE l.vn_id = ?")) {
            ps.setString(1, vnId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                List<Developer> devs = VnRows.loadDevelopers(conn, "vd.vn_id = ?", vnId)
                        .getOrDefault(vnId, List.of());
                return Optional.of(mapEntry(rs, devs));
            }
        }
    }

    private static LibraryEntry mapEntry(ResultSet rs, List<Developer> devs) throws SQLException {
        VisualNovel vn = VnRows.map(rs, devs);
        return new LibraryEntry(vn, Instant.ofEpochSecond(rs.getLong("added_at")));
    }
}

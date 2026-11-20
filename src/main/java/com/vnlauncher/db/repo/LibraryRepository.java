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

    /** SELECT común: datos de la VN + columnas propias de la biblioteca. */
    private static final String SELECT_ENTRIES =
            "SELECT " + VnRows.COLUMNS + ", l.added_at, l.exe_path, l.use_locale_emulator, "
                    + "l.le_profile, l.last_played_at "
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

    /**
     * Guarda cómo se lanza el juego.
     *
     * @param exePath ruta absoluta del ejecutable (o null para olvidarlo)
     */
    public void updateLaunchConfig(String vnId, String exePath, boolean useLocaleEmulator) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE library_entry SET exe_path = ?, use_locale_emulator = ? WHERE vn_id = ?")) {
            ps.setString(1, exePath);
            ps.setInt(2, useLocaleEmulator ? 1 : 0); // SQLite no tiene BOOLEAN: 0/1
            ps.setString(3, vnId);
            ps.executeUpdate();
        }
    }

    /** Cambia solo el ejecutable (conserva la opción de Locale Emulator). */
    public void updateExePath(String vnId, String exePath) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE library_entry SET exe_path = ? WHERE vn_id = ?")) {
            ps.setString(1, exePath);
            ps.setString(2, vnId);
            ps.executeUpdate();
        }
    }

    /** Cambia solo la opción de Locale Emulator (conserva el ejecutable). */
    public void updateUseLocaleEmulator(String vnId, boolean use) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE library_entry SET use_locale_emulator = ? WHERE vn_id = ?")) {
            ps.setInt(1, use ? 1 : 0);
            ps.setString(2, vnId);
            ps.executeUpdate();
        }
    }

    public void updateLastPlayed(String vnId, Instant when) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE library_entry SET last_played_at = ? WHERE vn_id = ?")) {
            ps.setLong(1, when.getEpochSecond());
            ps.setString(2, vnId);
            ps.executeUpdate();
        }
    }

    private static LibraryEntry mapEntry(ResultSet rs, List<Developer> devs) throws SQLException {
        VisualNovel vn = VnRows.map(rs, devs);
        long lastPlayed = rs.getLong("last_played_at");
        Instant lastPlayedAt = rs.wasNull() ? null : Instant.ofEpochSecond(lastPlayed);
        return new LibraryEntry(
                vn,
                Instant.ofEpochSecond(rs.getLong("added_at")),
                rs.getString("exe_path"),
                rs.getInt("use_locale_emulator") == 1,
                rs.getString("le_profile"),
                lastPlayedAt);
    }
}

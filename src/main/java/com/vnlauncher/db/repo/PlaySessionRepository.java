package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Sesiones de juego (tabla play_session). Una sesión pasa por tres momentos:
 * <ol>
 *   <li>{@link #start}: se crea al lanzar el juego, sin ended_at ("en curso");</li>
 *   <li>{@link #heartbeat}: mientras sigue abierto se actualiza last_seen_at cada poco;</li>
 *   <li>{@link #finish}: al cerrarse el juego se rellenan ended_at y duration_seconds.</li>
 * </ol>
 * El tiempo total de una VN es la SUMA de las duraciones: no se guarda aparte, así nunca se
 * desincroniza.
 */
public class PlaySessionRepository {

    private final Database db;

    public PlaySessionRepository(Database db) {
        this.db = db;
    }

    /** Crea una sesión en curso y devuelve su id. */
    public long start(String vnId, Instant startedAt) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO play_session (vn_id, started_at, last_seen_at) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, vnId);
            ps.setLong(2, startedAt.getEpochSecond());
            ps.setLong(3, startedAt.getEpochSecond());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** "El juego seguía abierto a esta hora". */
    public void heartbeat(long sessionId, Instant lastSeen) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE play_session SET last_seen_at = ? WHERE id = ?")) {
            ps.setLong(1, lastSeen.getEpochSecond());
            ps.setLong(2, sessionId);
            ps.executeUpdate();
        }
    }

    /** Cierra la sesión: el juego se vio por última vez en {@code endedAt}. */
    public void finish(long sessionId, Instant endedAt) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE play_session
                     SET ended_at = ?, last_seen_at = ?, duration_seconds = MAX(0, ? - started_at)
                     WHERE id = ?
                     """)) {
            long end = endedAt.getEpochSecond();
            ps.setLong(1, end);
            ps.setLong(2, end);
            ps.setLong(3, end);
            ps.setLong(4, sessionId);
            ps.executeUpdate();
        }
    }

    /** Borra una sesión (p. ej. el juego no llegó a arrancar). */
    public void delete(long sessionId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM play_session WHERE id = ?")) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
        }
    }

    /**
     * Cierra las sesiones que quedaron "en curso" porque el launcher se cerró de golpe. Se llama al
     * arrancar. Como fin se usa el último latido (COALESCE: el primer valor que no sea NULL).
     *
     * @return cuántas sesiones se han cerrado
     */
    public int closeUnfinished() throws SQLException {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    UPDATE play_session
                    SET ended_at = COALESCE(last_seen_at, started_at),
                        duration_seconds = MAX(0, COALESCE(last_seen_at, started_at) - started_at)
                    WHERE ended_at IS NULL
                    """);
        }
    }

    /** Segundos jugados de cada VN (solo sesiones cerradas), con una sola consulta. */
    public Map<String, Long> totalSecondsByVn() throws SQLException {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT vn_id, SUM(duration_seconds) AS total
                     FROM play_session
                     WHERE duration_seconds IS NOT NULL
                     GROUP BY vn_id
                     """)) {
            Map<String, Long> totals = new HashMap<>();
            while (rs.next()) {
                totals.put(rs.getString("vn_id"), rs.getLong("total"));
            }
            return totals;
        }
    }

    public long totalSeconds(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(SUM(duration_seconds), 0) FROM play_session WHERE vn_id = ?")) {
            ps.setString(1, vnId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }
}

package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.ShelfColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** SQL de las estanterías (tablas shelf y shelf_entry, migración 007). */
public class ShelfRepository {

    /** Cada estantería con cuántas VNs tiene (LEFT JOIN: también las vacías, con 0). */
    private static final String SELECT_WITH_COUNT = """
            SELECT s.id, s.name, s.color, s.position, COUNT(e.vn_id) AS novels
            FROM shelf s LEFT JOIN shelf_entry e ON e.shelf_id = s.id
            """;

    private final Database db;

    public ShelfRepository(Database db) {
        this.db = db;
    }

    /** Todas, en el orden de la barra lateral. */
    public List<Shelf> findAll() throws SQLException {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(SELECT_WITH_COUNT + "GROUP BY s.id ORDER BY s.position, s.id")) {
            return map(rs);
        }
    }

    public Optional<Shelf> find(long id) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_WITH_COUNT + "WHERE s.id = ? GROUP BY s.id")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs).stream().findFirst();
            }
        }
    }

    /** Las estanterías en las que está una VN (para los chips de su ficha). */
    public List<Shelf> findByVn(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_WITH_COUNT
                     + "WHERE s.id IN (SELECT shelf_id FROM shelf_entry WHERE vn_id = ?) "
                     + "GROUP BY s.id ORDER BY s.position, s.id")) {
            ps.setString(1, vnId);
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        }
    }

    /** Crea una estantería al final de la lista. */
    public Shelf create(String name, ShelfColor color, Instant createdAt) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO shelf (name, color, position, created_at) "
                             + "VALUES (?, ?, (SELECT COALESCE(MAX(position) + 1, 0) FROM shelf), ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, color.key());
            ps.setLong(3, createdAt.getEpochSecond());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return find(keys.getLong(1)).orElseThrow();
            }
        }
    }

    public void update(long id, String name, ShelfColor color) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE shelf SET name = ?, color = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, color.key());
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    /** Borra la estantería. Sus VNs siguen en la biblioteca: solo desaparece la lista (CASCADE). */
    public void delete(long id) throws SQLException {
        db.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM shelf WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            renumber(conn); // que no queden huecos en las posiciones
        });
    }

    /**
     * Pone la estantería en {@code newIndex} (0 = arriba del todo) y corre las demás. Todo en una
     * transacción: o se reordena entera o no cambia nada.
     */
    public void moveTo(long id, int newIndex) throws SQLException {
        db.inTransaction(conn -> {
            List<Long> ids = idsInOrder(conn);
            if (!ids.remove(id)) {
                return;
            }
            ids.add(Math.max(0, Math.min(newIndex, ids.size())), id);
            savePositions(conn, ids);
        });
    }

    /** Mete una VN en la estantería (si ya estaba, no pasa nada). */
    public void addVn(long shelfId, String vnId, Instant addedAt) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO shelf_entry (shelf_id, vn_id, added_at) VALUES (?, ?, ?)")) {
            ps.setLong(1, shelfId);
            ps.setString(2, vnId);
            ps.setLong(3, addedAt.getEpochSecond());
            ps.executeUpdate();
        }
    }

    public void removeVn(long shelfId, String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM shelf_entry WHERE shelf_id = ? AND vn_id = ?")) {
            ps.setLong(1, shelfId);
            ps.setString(2, vnId);
            ps.executeUpdate();
        }
    }

    /** Las VNs de una estantería (para filtrar la biblioteca). */
    public Set<String> vnIdsIn(long shelfId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT vn_id FROM shelf_entry WHERE shelf_id = ?")) {
            ps.setLong(1, shelfId);
            Set<String> ids = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
            return ids;
        }
    }

    private static List<Long> idsInOrder(Connection conn) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM shelf ORDER BY position, id")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static void renumber(Connection conn) throws SQLException {
        savePositions(conn, idsInOrder(conn));
    }

    private static void savePositions(Connection conn, List<Long> ids) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE shelf SET position = ? WHERE id = ?")) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(1, i);
                ps.setLong(2, ids.get(i));
                ps.addBatch(); // todas las actualizaciones de golpe
            }
            ps.executeBatch();
        }
    }

    private static List<Shelf> map(ResultSet rs) throws SQLException {
        List<Shelf> shelves = new ArrayList<>();
        while (rs.next()) {
            shelves.add(new Shelf(rs.getLong("id"), rs.getString("name"),
                    // La columna tiene un CHECK con las 8 claves: si no se reconoce, mejor rosa que fallar
                    ShelfColor.fromKey(rs.getString("color")).orElse(ShelfColor.PINK),
                    rs.getInt("position"), rs.getInt("novels")));
        }
        return shelves;
    }
}

package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.RouteSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acceso a la tabla route. Aquí solo hay SQL: las reglas (orden, fecha de completado...) están en RouteService. */
public class RouteRepository {

    private static final String COLUMNS =
            "id, vn_id, character_id, name, recommended_order, status, notes, completed_at";

    private final Database db;

    public RouteRepository(Database db) {
        this.db = db;
    }

    /** Inserta una ruta nueva (con id null) y la devuelve con el id que ha generado la BD. */
    public Route insert(Route route) throws SQLException {
        try (Connection conn = db.getConnection();
             // RETURN_GENERATED_KEYS: pedimos a JDBC que nos devuelva el id AUTOINCREMENT creado.
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO route (vn_id, character_id, name, recommended_order, status, notes, completed_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, route.vnId());
            bindEditableColumns(ps, 2, route);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new Route(id, route.vnId(), route.characterId(), route.name(), route.recommendedOrder(),
                        route.status(), route.notes(), route.completedAt());
            }
        }
    }

    /** Actualiza todos los campos editables de una ruta existente. */
    public void update(Route route) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE route
                     SET character_id = ?, name = ?, recommended_order = ?, status = ?, notes = ?, completed_at = ?
                     WHERE id = ?
                     """)) {
            bindEditableColumns(ps, 1, route);
            ps.setLong(7, route.id());
            ps.executeUpdate();
        }
    }

    /**
     * Asigna el orden recomendado de varias rutas a la vez (en una transacción, para que nunca
     * queden dos rutas con el mismo número si algo falla a medias).
     */
    public void updateOrders(List<Route> routes) throws SQLException {
        db.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE route SET recommended_order = ? WHERE id = ?")) {
                for (Route r : routes) {
                    ps.setInt(1, r.recommendedOrder());
                    ps.setLong(2, r.id());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public void delete(long id) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM route WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public Optional<Route> findById(long id) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + COLUMNS + " FROM route WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Rutas de una VN en el orden recomendado (y, a igualdad, en el orden en que se crearon). */
    public List<Route> findByVn(String vnId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + COLUMNS + " FROM route WHERE vn_id = ? ORDER BY recommended_order, id")) {
            ps.setString(1, vnId);
            List<Route> routes = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    routes.add(map(rs));
                }
            }
            return routes;
        }
    }

    /**
     * Recuento de rutas por VN con una sola consulta. GROUP BY agrupa las filas de cada vn_id;
     * SUM(status = 'COMPLETED') cuenta las completadas porque en SQLite una comparación vale 1 o 0.
     */
    public Map<String, RouteSummary> summariesByVn() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT vn_id,
                            COUNT(*)                     AS total,
                            SUM(status = 'COMPLETED')    AS completed,
                            SUM(status = 'IN_PROGRESS')  AS in_progress
                     FROM route
                     GROUP BY vn_id
                     """);
             ResultSet rs = ps.executeQuery()) {
            Map<String, RouteSummary> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("vn_id"), new RouteSummary(
                        rs.getInt("total"), rs.getInt("completed"), rs.getInt("in_progress")));
            }
            return result;
        }
    }

    /** Rellena los 6 parámetros editables empezando en la posición {@code first}. */
    private static void bindEditableColumns(PreparedStatement ps, int first, Route route) throws SQLException {
        ps.setString(first, route.characterId());           // setString(null) guarda NULL
        ps.setString(first + 1, route.name());
        ps.setInt(first + 2, route.recommendedOrder());
        ps.setString(first + 3, route.status().name());     // "PENDING", "IN_PROGRESS"...
        ps.setString(first + 4, route.notes());
        if (route.completedAt() == null) {
            ps.setNull(first + 5, Types.INTEGER);
        } else {
            ps.setLong(first + 5, route.completedAt().getEpochSecond());
        }
    }

    private static Route map(ResultSet rs) throws SQLException {
        long completed = rs.getLong("completed_at");
        Instant completedAt = rs.wasNull() ? null : Instant.ofEpochSecond(completed);
        return new Route(
                rs.getLong("id"),
                rs.getString("vn_id"),
                rs.getString("character_id"),
                rs.getString("name"),
                rs.getInt("recommended_order"),
                RouteStatus.valueOf(rs.getString("status")), // "IN_PROGRESS" -> RouteStatus.IN_PROGRESS
                rs.getString("notes"),
                completedAt);
    }
}

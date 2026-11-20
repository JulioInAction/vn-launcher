package com.vnlauncher.db.repo;

import com.vnlauncher.model.Developer;
import com.vnlauncher.model.VisualNovel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Código compartido por los repositorios que leen VNs: convertir una fila de la tabla vn
 * en un objeto VisualNovel y cargar sus desarrolladores.
 * Package-private: es un detalle interno de la capa de base de datos.
 */
final class VnRows {

    /** Columnas de vn en el orden que espera {@link #map}. Con alias "v" para poder hacer JOIN. */
    static final String COLUMNS = "v.id, v.title, v.alt_title, v.description, v.released, "
            + "v.image_url, v.image_path, v.image_sexual, v.image_violence";

    private VnRows() {
    }

    static VisualNovel map(ResultSet rs, List<Developer> developers) throws SQLException {
        return new VisualNovel(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("alt_title"),
                rs.getString("description"),
                rs.getString("released"),
                rs.getString("image_url"),
                rs.getString("image_path"),
                getNullableDouble(rs, "image_sexual"),
                getNullableDouble(rs, "image_violence"),
                developers);
    }

    /**
     * Trampa clásica de JDBC: getDouble() devuelve 0.0 cuando la columna es NULL.
     * Para distinguir "0" de "sin dato" hay que preguntar a wasNull() justo después.
     */
    static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    /** Setter equivalente para escribir: un Double null se guarda como NULL, no como 0. */
    static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    /**
     * Carga los desarrolladores de VARIAS VNs con una sola consulta y los agrupa por vn_id.
     *
     * <p>Evita el "problema N+1": si para mostrar 100 VNs hiciéramos 1 consulta para la lista
     * y luego 1 consulta de desarrolladores por cada VN, serían 101 consultas.
     *
     * @param whereSql condición sobre vd (vn_developer), p. ej. "vd.vn_id = ?"
     * @param params   valores para los "?" de whereSql
     */
    static Map<String, List<Developer>> loadDevelopers(Connection conn, String whereSql, Object... params)
            throws SQLException {
        String sql = """
                SELECT vd.vn_id, d.id, d.name, d.original
                FROM vn_developer vd
                JOIN developer d ON d.id = vd.developer_id
                WHERE %s
                ORDER BY d.name
                """.formatted(whereSql);
        Map<String, List<Developer>> byVn = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]); // los índices de JDBC empiezan en 1
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Developer dev = new Developer(rs.getString("id"), rs.getString("name"), rs.getString("original"));
                    // computeIfAbsent: si aún no hay lista para esa VN, la crea; luego añade.
                    byVn.computeIfAbsent(rs.getString("vn_id"), k -> new ArrayList<>()).add(dev);
                }
            }
        }
        return byVn;
    }
}

package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Ajustes de la aplicación como pares clave/valor (tabla setting).
 * Es la forma más simple de guardar opciones sueltas sin crear una columna por cada una.
 */
public class SettingsRepository {

    private final Database db;

    public SettingsRepository(Database db) {
        this.db = db;
    }

    public Optional<String> get(String key) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT value FROM setting WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                // Optional.ofNullable: si la fila existe pero value es NULL, también "vacío"
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    /** Crea o sobrescribe el ajuste (UPSERT, como en VnRepository). */
    public void put(String key, String value) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO setting (key, value) VALUES (?, ?) "
                             + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public void remove(String key) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM setting WHERE key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
    }
}

package com.vnlauncher.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    // JUnit crea una carpeta temporal nueva para cada test y la borra al terminar.
    @TempDir
    Path tempDir;

    private Database db;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db"));
        db.migrate();
    }

    @Test
    void migrateCreatesAllTables() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             // sqlite_master es la tabla interna donde SQLite guarda el esquema
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        assertTrue(tables.containsAll(List.of(
                "vn", "developer", "vn_developer", "character", "vn_character",
                "library_entry", "route", "play_session", "setting")), "Tablas encontradas: " + tables);
        assertEquals(2, db.schemaVersion());
    }

    @Test
    void migrateIsIdempotent() throws SQLException {
        // Una segunda llamada no debe fallar intentando crear de nuevo las tablas.
        db.migrate();
        assertEquals(2, db.schemaVersion());
    }

    @Test
    void upgradingFromVersion1KeepsExistingData() throws SQLException {
        // Simula la base de datos de alguien que usó la app antes de la migración 2:
        // se crea en otra carpeta, se aplica solo la 001 a mano y se mete una sesión.
        Database old = new Database(tempDir.resolve("old.db"));
        try (Connection c = old.getConnection(); Statement st = c.createStatement()) {
            for (String sql : Database.splitStatements(new String(
                    Database.class.getResourceAsStream("migration/001_initial.sql").readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8))) {
                st.execute(sql);
            }
            st.execute("PRAGMA user_version = 1");
            st.executeUpdate("INSERT INTO vn (id, title, fetched_at) VALUES ('v1', 'Test', 0)");
            st.executeUpdate("INSERT INTO library_entry (vn_id, added_at) VALUES ('v1', 0)");
            st.executeUpdate("INSERT INTO play_session (vn_id, started_at, ended_at, duration_seconds) "
                    + "VALUES ('v1', 100, 400, 300)");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        old.migrate(); // aplica solo la 002

        assertEquals(2, old.schemaVersion());
        try (Connection c = old.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT duration_seconds, last_seen_at FROM play_session")) {
            assertTrue(rs.next());
            assertEquals(300, rs.getInt(1), "La sesión antigua se conserva");
            rs.getLong(2);
            assertTrue(rs.wasNull(), "La columna nueva empieza vacía");
        }
    }

    @Test
    void foreignKeysAreEnforced() throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            // 'v999' no existe en vn -> con foreign_keys=ON esto debe fallar.
            SQLException e = assertThrows(SQLException.class, () ->
                    st.executeUpdate("INSERT INTO library_entry (vn_id, added_at) VALUES ('v999', 0)"));
            assertTrue(e.getMessage().contains("FOREIGN KEY"), e.getMessage());
        }
    }

    @Test
    void routeStatusOnlyAcceptsKnownValues() throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO vn (id, title, fetched_at) VALUES ('v1', 'Test', 0)");
            st.executeUpdate("INSERT INTO library_entry (vn_id, added_at) VALUES ('v1', 0)");
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO route (vn_id, name, status) VALUES ('v1', 'Ruta', 'TERMINADA')"));
        }
    }

    @Test
    void removingFromLibraryCascadesToRoutesAndSessions() throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO vn (id, title, fetched_at) VALUES ('v1', 'Test', 0)");
            st.executeUpdate("INSERT INTO library_entry (vn_id, added_at) VALUES ('v1', 0)");
            st.executeUpdate("INSERT INTO route (vn_id, name) VALUES ('v1', 'Ruta A')");
            st.executeUpdate("INSERT INTO play_session (vn_id, started_at) VALUES ('v1', 0)");

            st.executeUpdate("DELETE FROM library_entry WHERE vn_id = 'v1'");

            assertEquals(0, count(st, "route"));
            assertEquals(0, count(st, "play_session"));
            assertEquals(1, count(st, "vn"), "La caché de VNDB se conserva");
        }
    }

    @Test
    void splitStatementsIgnoresCommentsAndBlankParts() {
        String script = """
                -- comentario
                CREATE TABLE a (x INTEGER); -- otro comentario

                CREATE TABLE b (y TEXT);
                """;
        assertEquals(List.of("CREATE TABLE a (x INTEGER)", "CREATE TABLE b (y TEXT)"),
                Database.splitStatements(script));
    }

    private static int count(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getInt(1);
        }
    }
}

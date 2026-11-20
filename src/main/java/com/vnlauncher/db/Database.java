package com.vnlauncher.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Punto de acceso a la base de datos SQLite: abre conexiones y aplica las migraciones.
 *
 * <p><b>Una conexión por operación.</b> En vez de compartir una única Connection en toda la app,
 * cada repositorio pedirá una con {@link #getConnection()} dentro de un try-with-resources.
 * Con SQLite abrir una conexión es barato (es abrir un fichero), y así evitamos problemas
 * cuando varios hilos (descargas en segundo plano, la interfaz...) usan la BD a la vez.
 */
public class Database {

    /**
     * Migraciones en orden. La posición en la lista (empezando en 1) es el número de versión.
     * NUNCA se modifica una migración ya publicada: para cambiar el esquema se añade otra nueva.
     * (Hay que listarlas a mano porque Java no puede "listar" ficheros dentro de un .jar fácilmente.)
     */
    private static final List<String> MIGRATIONS = List.of(
            "migration/001_initial.sql",
            "migration/002_play_session_last_seen.sql"
    );

    private final String url;

    /** @param file fichero .db; si no existe, SQLite lo crea al abrir la primera conexión. */
    public Database(Path file) {
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
    }

    /**
     * Abre una conexión nueva. Quien la pide es responsable de cerrarla (try-with-resources).
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            // SQLite trae las claves foráneas DESACTIVADAS por defecto, y es un ajuste
            // por conexión: si no lo hacemos aquí, los REFERENCES y ON DELETE CASCADE se ignoran.
            st.execute("PRAGMA foreign_keys = ON");
            // Si otro hilo está escribiendo, esperar hasta 5 s en vez de fallar con "database is locked".
            st.execute("PRAGMA busy_timeout = 5000");
        }
        return conn;
    }

    /** Trabajo con la BD que puede lanzar SQLException (las lambdas normales no pueden). */
    @FunctionalInterface
    public interface SqlWork {
        void run(Connection conn) throws SQLException;
    }

    /**
     * Ejecuta varias sentencias como UNA transacción: o se aplican todas o ninguna.
     * Ejemplo: guardar una VN y sus desarrolladores; si falla a medias, no queda una VN
     * sin desarrolladores o con los de antes mezclados.
     */
    public void inTransaction(SqlWork work) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Versión actual del esquema, guardada por SQLite en la cabecera del fichero. */
    public int schemaVersion() throws SQLException {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    /**
     * Aplica las migraciones pendientes. Es idempotente: si la BD ya está al día no hace nada,
     * así que se puede llamar en cada arranque.
     */
    public void migrate() throws SQLException {
        try (Connection conn = getConnection()) {
            // WAL (write-ahead log) permite leer mientras otro hilo escribe. A diferencia de
            // foreign_keys, este modo se guarda en el fichero, basta activarlo una vez.
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");
            }

            int current = schemaVersion();
            for (int version = current + 1; version <= MIGRATIONS.size(); version++) {
                applyMigration(conn, version, MIGRATIONS.get(version - 1));
            }
        }
    }

    private void applyMigration(Connection conn, int version, String resource) throws SQLException {
        // Transacción: o se aplica la migración entera o nada. Si un CREATE falla a medias,
        // el rollback deja la BD como estaba y no quedamos con un esquema "medio hecho".
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            for (String sql : splitStatements(readResource(resource))) {
                st.execute(sql);
            }
            // PRAGMA no admite parámetros (?), por eso concatenamos; version es un int nuestro, no hay riesgo.
            st.execute("PRAGMA user_version = " + version);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw new SQLException("Error aplicando la migración " + resource, e);
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static String readResource(String name) {
        // getResourceAsStream relativo a esta clase: busca en com/vnlauncher/db/<name>
        try (InputStream in = Database.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("No se encuentra el recurso " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * JDBC ejecuta una sentencia cada vez, así que partimos el script por ';'.
     * Antes quitamos los comentarios "--". Es un parser sencillo: no soportaría ';' dentro
     * de textos o triggers, pero nuestros scripts de migración no los usan.
     */
    static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {   // \R = cualquier salto de línea (\n o \r\n)
            int comment = line.indexOf("--");
            withoutComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            if (!part.isBlank()) {
                statements.add(part.strip());
            }
        }
        return statements;
    }
}

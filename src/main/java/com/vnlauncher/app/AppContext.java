package com.vnlauncher.app;

import com.vnlauncher.db.Database;
import com.vnlauncher.util.AppPaths;

import java.sql.SQLException;

/**
 * "Inyección de dependencias" manual: aquí se crean UNA vez todos los objetos compartidos
 * (base de datos, y en próximas fases el cliente de VNDB, repositorios y servicios)
 * y se conectan entre sí pasándolos por constructor.
 *
 * <p>Ventaja frente a usar singletons estáticos: cada clase declara en su constructor lo que
 * necesita, y en los tests podemos construirlas con una BD temporal en vez de la real.
 */
public final class AppContext {

    private final AppPaths paths;
    private final Database database;

    private AppContext(AppPaths paths, Database database) {
        this.paths = paths;
        this.database = database;
    }

    /** Prepara carpetas y base de datos. Si algo falla aquí la app no puede arrancar. */
    public static AppContext create() throws SQLException {
        AppPaths paths = AppPaths.fromEnvironment();
        paths.createDirectories();

        Database database = new Database(paths.databaseFile());
        database.migrate();

        return new AppContext(paths, database);
    }

    public AppPaths paths() {
        return paths;
    }

    public Database database() {
        return database;
    }
}

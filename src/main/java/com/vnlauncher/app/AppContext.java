package com.vnlauncher.app;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.db.Database;
import com.vnlauncher.service.CatalogService;
import com.vnlauncher.util.AppPaths;
import com.vnlauncher.util.Sleeper;

import java.net.http.HttpClient;
import java.sql.SQLException;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Inyección de dependencias" manual: aquí se crean UNA vez todos los objetos compartidos
 * (base de datos, cliente de VNDB, servicios...) y se conectan entre sí pasándolos por constructor.
 *
 * <p>Ventaja frente a usar singletons estáticos: cada clase declara en su constructor lo que
 * necesita, y en los tests podemos construirlas con dependencias falsas o temporales.
 */
public final class AppContext {

    /**
     * VNDB permite 200 peticiones cada 5 minutos. Nos quedamos en 190 como margen de seguridad
     * (por si VNDB cuenta alguna petición que nosotros no, p. ej. de otra herramienta tuya).
     */
    private static final int VNDB_MAX_REQUESTS = 190;
    private static final Duration VNDB_WINDOW = Duration.ofMinutes(5);

    private final AppPaths paths;
    private final Database database;
    private final HttpClient httpClient;
    private final ExecutorService background;
    private final CatalogService catalogService;

    private AppContext(AppPaths paths, Database database, HttpClient httpClient,
                       ExecutorService background, CatalogService catalogService) {
        this.paths = paths;
        this.database = database;
        this.httpClient = httpClient;
        this.background = background;
        this.catalogService = catalogService;
    }

    /** Prepara carpetas, base de datos y servicios. Si algo falla aquí la app no puede arrancar. */
    public static AppContext create() throws SQLException {
        AppPaths paths = AppPaths.fromEnvironment();
        paths.createDirectories();

        Database database = new Database(paths.databaseFile());
        database.migrate();

        // Un único HttpClient para toda la app: reutiliza conexiones y es seguro entre hilos.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        Sleeper sleeper = Sleeper.real();
        RateLimiter rateLimiter = new RateLimiter(
                VNDB_MAX_REQUESTS, VNDB_WINDOW, InstantSource.system(), sleeper);
        VndbClient vndb = new VndbClient(httpClient, rateLimiter, sleeper, VndbClient.DEFAULT_BASE_URI);

        // Hilos virtuales (Java 21+): muy baratos, uno por tarea. Ideales para trabajo que pasa
        // casi todo el tiempo esperando (red, disco). Además no impiden que la app se cierre.
        ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

        return new AppContext(paths, database, httpClient, background, new CatalogService(vndb));
    }

    /** Se llama al cerrar la ventana: corta las tareas y conexiones pendientes. */
    public void shutdown() {
        background.shutdownNow();
        httpClient.shutdownNow();
    }

    public AppPaths paths() {
        return paths;
    }

    public Database database() {
        return database;
    }

    /** Ejecutor para tareas lentas (red, disco) que no deben bloquear la interfaz. */
    public ExecutorService background() {
        return background;
    }

    public CatalogService catalogService() {
        return catalogService;
    }
}

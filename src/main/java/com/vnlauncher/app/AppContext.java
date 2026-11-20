package com.vnlauncher.app;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.CharacterRepository;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.PlaySessionRepository;
import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.service.CatalogService;
import com.vnlauncher.service.CharacterService;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.LaunchService;
import com.vnlauncher.service.LibraryService;
import com.vnlauncher.service.PlaytimeTracker;
import com.vnlauncher.service.ProcessScanner;
import com.vnlauncher.service.ProcessStarter;
import com.vnlauncher.service.RouteService;
import com.vnlauncher.service.SettingsService;
import com.vnlauncher.util.AppPaths;
import com.vnlauncher.util.Sleeper;

import java.net.http.HttpClient;
import java.sql.SQLException;
import java.time.Clock;
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

    private static final System.Logger LOG = System.getLogger(AppContext.class.getName());

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
    private final LibraryService libraryService;
    private final ImageCacheService imageCache;
    private final ContentFilter contentFilter;
    private final CharacterService characterService;
    private final RouteService routeService;
    private final SettingsService settingsService;
    private final LaunchService launchService;
    private final RateLimiter rateLimiter;
    private final PlaytimeTracker playtimeTracker;

    // Constructor con muchos parámetros: es el precio de la inyección manual. Si crece demasiado,
    // se podría agrupar (p. ej. un record con los servicios), pero así se ve todo de un vistazo.
    private AppContext(AppPaths paths, Database database, HttpClient httpClient, ExecutorService background,
                       CatalogService catalogService, LibraryService libraryService,
                       ImageCacheService imageCache, ContentFilter contentFilter,
                       CharacterService characterService, RouteService routeService,
                       SettingsService settingsService, LaunchService launchService, RateLimiter rateLimiter,
                       PlaytimeTracker playtimeTracker) {
        this.paths = paths;
        this.database = database;
        this.httpClient = httpClient;
        this.background = background;
        this.catalogService = catalogService;
        this.libraryService = libraryService;
        this.imageCache = imageCache;
        this.contentFilter = contentFilter;
        this.characterService = characterService;
        this.routeService = routeService;
        this.settingsService = settingsService;
        this.launchService = launchService;
        this.rateLimiter = rateLimiter;
        this.playtimeTracker = playtimeTracker;
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

        // Repositorios (SQL) y servicios (lógica) que los usan.
        VnRepository vnRepository = new VnRepository(database);
        LibraryRepository libraryRepository = new LibraryRepository(database);
        ImageCacheService imageCache = new ImageCacheService(httpClient, paths);
        Clock clock = Clock.systemUTC();
        RouteRepository routeRepository = new RouteRepository(database);
        PlaySessionRepository sessionRepository = new PlaySessionRepository(database);
        // Sesiones que quedaron abiertas porque la app se cerró de golpe: cerrarlas con su último latido
        int recovered = sessionRepository.closeUnfinished();
        if (recovered > 0) {
            LOG.log(System.Logger.Level.INFO, "Sesiones de juego recuperadas (no se cerraron bien): {0}", recovered);
        }
        LibraryService libraryService = new LibraryService(
                vnRepository, libraryRepository, routeRepository, sessionRepository, imageCache, clock);
        CharacterService characterService = new CharacterService(
                vndb, new CharacterRepository(database), imageCache, clock);
        SettingsService settingsService = new SettingsService(new SettingsRepository(database));
        // Vigila los juegos abiertos (cada 5 s, en su propio hilo)
        PlaytimeTracker playtimeTracker = new PlaytimeTracker(sessionRepository, ProcessScanner.system(), clock);
        playtimeTracker.startPolling();
        LaunchService launchService = new LaunchService(
                libraryRepository, settingsService, ProcessStarter.system(), playtimeTracker, clock);

        return new AppContext(paths, database, httpClient, background,
                new CatalogService(vndb), libraryService, imageCache, ContentFilter.withDefaults(),
                characterService, new RouteService(routeRepository, clock),
                settingsService, launchService, rateLimiter, playtimeTracker);
    }

    /** Se llama al cerrar la ventana: corta las tareas y conexiones pendientes. */
    public void shutdown() {
        playtimeTracker.shutdown(); // cierra las sesiones de los juegos que sigan abiertos
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

    public LibraryService libraryService() {
        return libraryService;
    }

    public ImageCacheService imageCache() {
        return imageCache;
    }

    public ContentFilter contentFilter() {
        return contentFilter;
    }

    public CharacterService characterService() {
        return characterService;
    }

    public RouteService routeService() {
        return routeService;
    }

    public SettingsService settingsService() {
        return settingsService;
    }

    public LaunchService launchService() {
        return launchService;
    }

    public PlaytimeTracker playtimeTracker() {
        return playtimeTracker;
    }

    /** Para mostrar en el menú lateral cuántas peticiones a VNDB se han hecho en los últimos 5 min. */
    public RateLimiter rateLimiter() {
        return rateLimiter;
    }
}

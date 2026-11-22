package com.vnlauncher.app;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.CharacterRepository;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.PlaySessionRepository;
import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.db.repo.ShelfRepository;
import com.vnlauncher.db.repo.VnDetailsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.service.BackupService;
import com.vnlauncher.service.CacheCleanupService;
import com.vnlauncher.service.CatalogService;
import com.vnlauncher.service.CharacterService;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.CoverService;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.LaunchService;
import com.vnlauncher.service.LibraryService;
import com.vnlauncher.service.PlaytimeTracker;
import com.vnlauncher.service.PreviewService;
import com.vnlauncher.service.ProfileService;
import com.vnlauncher.service.ProcessScanner;
import com.vnlauncher.service.ProcessStarter;
import com.vnlauncher.service.RefreshService;
import com.vnlauncher.service.RestoreService;
import com.vnlauncher.service.RouteService;
import com.vnlauncher.service.ScreenshotService;
import com.vnlauncher.service.SettingsService;
import com.vnlauncher.service.ShelfService;
import com.vnlauncher.service.UpdateService;
import com.vnlauncher.service.VnDetailsService;
import com.vnlauncher.util.AppPaths;
import com.vnlauncher.util.Sleeper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
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
    private final RefreshService refreshService;
    private final CoverService coverService;
    private final VnDetailsService vnDetailsService;
    private final ScreenshotService screenshotService;
    private final BackupService backupService;
    private final RestoreService restoreService;
    private final ProfileService profileService;
    private final ShelfService shelfService;
    private final PreviewService previewService;
    private final CacheCleanupService cacheCleanupService;
    private final UpdateService updateService;
    /** Si falló la copia de antes de actualizar (se avisa al abrir la ventana); si no, null. */
    private final Exception preMigrationBackupError;

    // Constructor con muchos parámetros: es el precio de la inyección manual. Si crece demasiado,
    // se podría agrupar (p. ej. un record con los servicios), pero así se ve todo de un vistazo.
    private AppContext(AppPaths paths, Database database, HttpClient httpClient, ExecutorService background,
                       CatalogService catalogService, LibraryService libraryService,
                       ImageCacheService imageCache, ContentFilter contentFilter,
                       CharacterService characterService, RouteService routeService,
                       SettingsService settingsService, LaunchService launchService, RateLimiter rateLimiter,
                       PlaytimeTracker playtimeTracker, CoverService coverService,
                       VnDetailsService vnDetailsService, BackupService backupService,
                       ProfileService profileService, ShelfService shelfService,
                       PreviewService previewService, CacheCleanupService cacheCleanupService,
                       UpdateService updateService,
                       Exception preMigrationBackupError) {
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
        // Solo combina servicios que ya existen: se crea aquí y no hace falta otro parámetro más
        this.refreshService = new RefreshService(catalogService, libraryService, characterService,
                vnDetailsService);
        this.coverService = coverService;
        this.vnDetailsService = vnDetailsService;
        this.backupService = backupService;
        this.restoreService = new RestoreService(paths, backupService);
        this.profileService = profileService;
        this.shelfService = shelfService;
        this.previewService = previewService;
        this.cacheCleanupService = cacheCleanupService;
        this.updateService = updateService;
        this.preMigrationBackupError = preMigrationBackupError;
        // Solo necesita la caché de imágenes: se crea aquí, como RefreshService
        this.screenshotService = new ScreenshotService(imageCache);
    }

    /**
     * Prepara carpetas, base de datos y servicios. Si algo falla aquí la app no puede arrancar.
     *
     * @param backupBeforeMigration false justo después de restaurar una copia o importar un perfil:
     *                              hay una copia de cómo estaba antes y la base nueva es un archivo
     *                              que el usuario ya tiene; no hace falta otra más
     */
    public static AppContext create(AppPaths paths, boolean backupBeforeMigration) throws SQLException {
        paths.createDirectories();

        Database database = new Database(paths.databaseFile());
        Clock clock = Clock.systemDefaultZone();
        BackupService backupService = new BackupService(database, paths.backupsDir(), clock, AppInfo.VERSION);
        // Si la app se acaba de actualizar (hay migraciones pendientes), copia ANTES de migrar
        Exception backupError = null;
        try {
            if (backupBeforeMigration) {
                backupService.beforeMigration();
            }
        } catch (IOException | SQLException e) {
            // Sin copia (disco lleno...) se sigue igualmente: las migraciones van en una transacción, y
            // dejar la app sin arrancar sería peor. Queda en el registro y se avisa al abrir la ventana.
            LOG.log(System.Logger.Level.ERROR, "No se pudo hacer la copia de antes de actualizar", e);
            backupError = e;
        }
        database.migrate();

        // Un único HttpClient para toda la app: reutiliza conexiones y es seguro entre hilos.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        Sleeper sleeper = Sleeper.real();
        // -Dvnlauncher.vndb.maxRequests=3 baja el límite: solo sirve para probar el aviso de límite
        int maxRequests = Integer.getInteger("vnlauncher.vndb.maxRequests", VNDB_MAX_REQUESTS);
        RateLimiter rateLimiter = new RateLimiter(maxRequests, VNDB_WINDOW, InstantSource.system(), sleeper);
        VndbClient vndb = new VndbClient(httpClient, rateLimiter, sleeper, VndbClient.DEFAULT_BASE_URI);

        // Hilos virtuales (Java 21+): muy baratos, uno por tarea. Ideales para trabajo que pasa
        // casi todo el tiempo esperando (red, disco). Además no impiden que la app se cierre.
        ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

        // Repositorios (SQL) y servicios (lógica) que los usan.
        VnRepository vnRepository = new VnRepository(database);
        LibraryRepository libraryRepository = new LibraryRepository(database);
        ImageCacheService imageCache = new ImageCacheService(httpClient, paths);
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
        ContentFilter contentFilter = ContentFilter.withDefaults();
        SettingsService settingsService = new SettingsService(new SettingsRepository(database), contentFilter);
        settingsService.loadContentFilter(); // nivel de difuminado guardado en Ajustes
        // Vigila los juegos abiertos (cada 5 s, en su propio hilo)
        PlaytimeTracker playtimeTracker = new PlaytimeTracker(sessionRepository, ProcessScanner.system(), clock);
        playtimeTracker.startPolling();
        LaunchService launchService = new LaunchService(
                libraryRepository, settingsService, ProcessStarter.system(), playtimeTracker, clock);

        CoverService coverService = new CoverService(vndb, libraryRepository, imageCache, paths, clock);
        VnDetailsService vnDetailsService = new VnDetailsService(vndb, new VnDetailsRepository(database), clock);

        return new AppContext(paths, database, httpClient, background,
                new CatalogService(vndb), libraryService, imageCache, contentFilter,
                characterService, new RouteService(routeRepository, clock),
                settingsService, launchService, rateLimiter, playtimeTracker, coverService, vnDetailsService,
                backupService, new ProfileService(database, paths, backupService, clock, AppInfo.VERSION),
                new ShelfService(new ShelfRepository(database), clock),
                new PreviewService(vnRepository, new VnDetailsRepository(database), clock),
                new CacheCleanupService(database, imageCache, clock),
                new UpdateService(httpClient, updatesEndpoint(), AppInfo.VERSION, settingsService, clock),
                backupError);
    }

    /**
     * Mantenimiento en segundo plano, una vez abierta la ventana (para no retrasar el arranque): la
     * copia de seguridad semanal, si toca. Si falla, queda en el registro: no es motivo para molestar.
     */
    public void startBackgroundMaintenance() {
        background.execute(() -> {
            try {
                backupService.weeklyIfDue(settingsService.weeklyBackups());
            } catch (IOException | SQLException e) {
                LOG.log(System.Logger.Level.WARNING, "No se pudo hacer la copia semanal", e);
            }
            try {
                cacheCleanupService.cleanUp(); // VNs vistas en la búsqueda y no añadidas, tras 30 días
            } catch (SQLException e) {
                LOG.log(System.Logger.Level.WARNING, "No se pudo limpiar la caché", e);
            }
        });
    }

    /** -Dvnlauncher.updates.url=http://localhost:8000/ apunta la comprobación a otro sitio: solo para probar el aviso. */
    private static URI updatesEndpoint() {
        String override = System.getProperty("vnlauncher.updates.url");
        return override == null ? UpdateService.LATEST_RELEASE : URI.create(override);
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

    /** "Cambiar portada" en la ficha, y la portada que se ve en cada sitio. */
    public CoverService coverService() {
        return coverService;
    }

    /** El error de la copia de antes de actualizar, si falló al arrancar. */
    public Optional<Exception> preMigrationBackupError() {
        return Optional.ofNullable(preMigrationBackupError);
    }

    /** Copias de seguridad automáticas de la base de datos. */
    public BackupService backupService() {
        return backupService;
    }

    /** Restaurar una copia de seguridad (se termina al reiniciar). */
    public RestoreService restoreService() {
        return restoreService;
    }

    /** Guardar lo visto en la búsqueda al abrir la vista previa o al añadir. */
    public PreviewService previewService() {
        return previewService;
    }

    /** Aviso de versiones nuevas (GitHub). */
    public UpdateService updateService() {
        return updateService;
    }

    /** «Mis estanterías». */
    public ShelfService shelfService() {
        return shelfService;
    }

    /** Exportar e importar el perfil (Ajustes). */
    public ProfileService profileService() {
        return profileService;
    }

    /** Imágenes de las capturas (miniaturas y tamaño completo) en la caché. */
    public ScreenshotService screenshotService() {
        return screenshotService;
    }

    /** Etiquetas y capturas de la ficha. */
    public VnDetailsService vnDetailsService() {
        return vnDetailsService;
    }

    public RefreshService refreshService() {
        return refreshService;
    }

    public PlaytimeTracker playtimeTracker() {
        return playtimeTracker;
    }

    /** Para mostrar en el menú lateral cuántas peticiones a VNDB se han hecho en los últimos 5 min. */
    public RateLimiter rateLimiter() {
        return rateLimiter;
    }
}

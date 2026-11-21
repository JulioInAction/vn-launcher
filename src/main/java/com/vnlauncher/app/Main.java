package com.vnlauncher.app;

import com.vnlauncher.model.Theme;
import com.vnlauncher.model.WindowBounds;
import com.vnlauncher.service.RestoreService;
import com.vnlauncher.ui.DebugFailures;
import com.vnlauncher.ui.ErrorReporter;
import com.vnlauncher.ui.Fonts;
import com.vnlauncher.ui.MainController;
import com.vnlauncher.ui.StartupNotices;
import com.vnlauncher.ui.Views;
import com.vnlauncher.util.AppPaths;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

/**
 * Punto de entrada. JavaFX llama a start() en el "hilo de la interfaz" (FX Application Thread):
 * todo lo que toque componentes gráficos debe ejecutarse en ese hilo.
 */
public class Main extends Application {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    /**
     * Ancho mínimo: menú (250) + márgenes (80) + panel de Buscar (540 + 24) + unos 300 para la lista
     * de resultados. En una pantalla más estrecha, el de la pantalla (ver {@link #minWidth()}).
     */
    private static final double MIN_WIDTH = 1200;
    private static final double MIN_HEIGHT = 680;

    private static final String BACKUP_FAILED = "No se ha podido hacer la copia de seguridad antes de actualizar.";
    private static final String BACKUP_FAILED_ADVICE = "Revisa que quede espacio libre en el disco. "
            + "La aplicación se ha actualizado igualmente y tus datos siguen en su sitio.";

    private AppContext context;
    private Stage stage;
    /** El bloqueo de instancia única; se suelta al cerrar. null si no se pudo crear el archivo. */
    private SingleInstance instance;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        AppPaths paths = AppPaths.fromEnvironment();
        // 1. Si es un reinicio (tras preparar una copia para restaurar), esperar a que se cierre la
        //    ventana anterior: hasta entonces la base de datos sigue siendo suya
        String previousPid = getParameters().getNamed().get(Relauncher.WAIT_FOR_PID);
        Relauncher.waitForPrevious(previousPid);
        // 2. Solo una VN Launcher a la vez. Antes que el registro: dos procesos no deben escribir
        //    en el mismo archivo
        if (!acquireInstance(paths, previousPid != null)) {
            Fonts.load();
            StartupNotices.alreadyRunning();
            Platform.exit();
            return;
        }
        // 3. El registro: así queda constancia hasta de un fallo al arrancar
        Path logFile = setUpLogging(paths);
        // 4. Manejador global de errores. Abre carpetas con getHostServices (el explorador de archivos)
        ErrorReporter.install(logFile, uri -> getHostServices().showDocument(uri));
        // Las fuentes tienen que estar registradas antes de que se aplique el CSS (también el del
        // diálogo de error si falla el arranque)
        Fonts.load();
        // 5. Lo que quedó pendiente (restaurar una copia) se termina ANTES de abrir la base de datos
        Optional<RestoreService.Outcome> pending = RestoreService.applyPending(paths);
        boolean restored = pending.filter(RestoreService.Outcome.Done.class::isInstance).isPresent();

        Theme theme;
        Optional<WindowBounds> savedWindow;
        try {
            DebugFailures.atStartup(); // solo con -Dvnlauncher.debug.fallo=arranque
            context = AppContext.create(paths, !restored);
            LOG.log(System.Logger.Level.INFO, "Arranque\n{0}",
                    SystemInfo.current(context.database().schemaVersion(), paths.root()));
            // Leer el tema guardado ANTES de crear la ventana, para que no se vea un "parpadeo"
            // del tema oscuro al arrancar con el claro
            theme = context.settingsService().theme();
            savedWindow = context.settingsService().windowBounds();
        } catch (Exception e) {
            ErrorReporter.showStartupError("No se ha podido preparar la base de datos.", e);
            return; // sin ventana principal, la app termina sola
        }

        try {
            // getHostServices().showDocument(url) abre una URL en el navegador del sistema.
            // Se lo pasamos al controlador como una función, para no atarlo a la clase Application.
            Parent root = Views.load("main-view.fxml",
                    new MainController(context, url -> getHostServices().showDocument(url), theme,
                            Relauncher::start));

            // Sin tamaño: lo pone placeWindow (el de la última vez, o uno grande la primera)
            Scene scene = new Scene(root);
            scene.getStylesheets().add(MainController.class.getResource("styles.css").toExternalForm());

            stage.setTitle("VN Launcher");
            // Icono de la ventana y de la barra de tareas (docs/diseño/logo/vn-launcher-app.png)
            stage.getIcons().add(new Image(
                    MainController.class.getResource("images/vn-launcher-app.png").toExternalForm()));
            stage.setMinWidth(minWidth());
            stage.setMinHeight(MIN_HEIGHT);
            placeWindow(stage, savedWindow);
            stage.setScene(scene);
            stage.show();
            // Desde ahora los diálogos de error se centran sobre la ventana y la tapan
            ErrorReporter.setOwner(root);
            context.startBackgroundMaintenance(); // copia semanal, si toca
            context.preMigrationBackupError().ifPresent(error ->
                    ErrorReporter.notifyUser(BACKUP_FAILED, BACKUP_FAILED_ADVICE, error));
            pending.ifPresent(outcome -> StartupNotices.afterPendingOperation(root, outcome));
            DebugFailures.afterStartup(context); // solo con -Dvnlauncher.debug.fallo=fx|tarea|hilo
        } catch (Exception e) {
            // Un error aquí suele ser un fallo en el FXML (fx:id mal escrito, etc.)
            ErrorReporter.showStartupError("No se ha podido cargar la interfaz.", e);
            Platform.exit();
        }
    }

    /**
     * Se queda con el bloqueo de instancia única. Al reiniciar se reintenta un rato, por si la
     * ventana anterior aún no lo ha soltado.
     *
     * @return false si ya hay otra VN Launcher abierta
     */
    private boolean acquireInstance(AppPaths paths, boolean relaunched) {
        try {
            instance = SingleInstance.acquire(paths.lockFile(), relaunched ? Relauncher.MAX_WAIT : Duration.ZERO)
                    .orElse(null);
            return instance != null;
        } catch (IOException e) {
            // Ni siquiera se puede crear el archivo (carpeta sin permisos...). Se sigue sin bloqueo:
            // si la carpeta de datos no sirve, el arranque fallará enseguida con su propio aviso
            LOG.log(System.Logger.Level.WARNING, "No se pudo crear el bloqueo de instancia única", e);
            return true;
        }
    }

    /**
     * Prepara el registro en logs/. Si no se puede (carpeta sin permisos...), la app sigue: los
     * mensajes salen solo por la consola.
     *
     * @return el archivo de registro actual, o null si no se pudo preparar
     */
    private static Path setUpLogging(AppPaths paths) {
        try {
            return LogSetup.configure(paths.logsDir());
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo preparar el registro en " + paths.logsDir(), e);
            return null;
        }
    }

    /** JavaFX llama a stop() al cerrar la última ventana. */
    @Override
    public void stop() {
        if (context != null) {
            saveWindow();
            context.shutdown();
        }
        LOG.log(System.Logger.Level.INFO, "Cierre");
        // Lo último: desde aquí puede abrirse otra VN Launcher (la del reinicio está esperando)
        if (instance != null) {
            instance.close();
        }
    }

    // ------------------------------------------------------------------ posición de la ventana

    /**
     * Abre la ventana donde y como se cerró la última vez. La primera vez (o si aquella posición
     * ya no cae en ninguna pantalla, p. ej. porque se ha desconectado un monitor), grande y centrada.
     */
    private static void placeWindow(Stage stage, Optional<WindowBounds> saved) {
        WindowBounds b = saved.filter(Main::isOnSomeScreen).orElseGet(Main::firstTimeBounds);
        // setWidth/setHeight son el tamaño de la ventana entera (con su marco), igual que lo que
        // se guarda al cerrar. Si se le diera ese tamaño a la Scene, crecería un poco cada vez.
        stage.setX(b.x());
        stage.setY(b.y());
        stage.setWidth(b.width());
        stage.setHeight(b.height());
        stage.setMaximized(b.maximized());
    }

    private static WindowBounds firstTimeBounds() {
        // getVisualBounds: la pantalla sin la barra de tareas de Windows
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        return WindowBounds.firstTime(screen.getMinX(), screen.getMinY(), screen.getWidth(), screen.getHeight(),
                minWidth(), MIN_HEIGHT);
    }

    /** MIN_WIDTH, sin pasar del ancho útil de la pantalla principal (p. ej. un portátil de 1152). */
    private static double minWidth() {
        return Math.min(MIN_WIDTH, Screen.getPrimary().getVisualBounds().getWidth());
    }

    /** true si la parte de arriba de la ventana (la barra de título) cae dentro de alguna pantalla. */
    private static boolean isOnSomeScreen(WindowBounds b) {
        return !Screen.getScreensForRectangle(b.x(), b.y(), Math.min(b.width(), 200), 40).isEmpty();
    }

    /** Guarda la posición y el tamaño para la próxima vez. */
    private void saveWindow() {
        if (stage == null || stage.isIconified()) {
            return; // minimizada, X e Y no valen (en Windows son -32000): se conserva lo anterior
        }
        try {
            WindowBounds bounds;
            if (stage.isMaximized()) {
                // Maximizada, X/Y/ancho/alto son los de la pantalla entera. Se guardan los de antes
                // de maximizar, para que al desmaximizar la próxima vez vuelva a ese tamaño.
                bounds = context.settingsService().windowBounds()
                        .orElseGet(Main::firstTimeBounds).withMaximized(true);
            } else {
                bounds = new WindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), false);
            }
            context.settingsService().setWindowBounds(bounds);
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo guardar el tamaño de la ventana", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

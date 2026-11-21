package com.vnlauncher.app;

import com.vnlauncher.model.Theme;
import com.vnlauncher.model.WindowBounds;
import com.vnlauncher.ui.Fonts;
import com.vnlauncher.ui.MainController;
import com.vnlauncher.ui.Views;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.sql.SQLException;
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

    private AppContext context;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Theme theme;
        Optional<WindowBounds> savedWindow;
        try {
            context = AppContext.create();
            // Leer el tema guardado ANTES de crear la ventana, para que no se vea un "parpadeo"
            // del tema oscuro al arrancar con el claro
            theme = context.settingsService().theme();
            savedWindow = context.settingsService().windowBounds();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Error al iniciar", e);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "No se pudo preparar la base de datos:\n" + e.getMessage());
            alert.setHeaderText("Error al iniciar VN Launcher");
            alert.showAndWait();
            return; // sin ventana principal, la app termina sola
        }

        try {
            // Las fuentes tienen que estar registradas antes de que se aplique el CSS
            Fonts.load();

            // getHostServices().showDocument(url) abre una URL en el navegador del sistema.
            // Se lo pasamos al controlador como una función, para no atarlo a la clase Application.
            Parent root = Views.load("main-view.fxml",
                    new MainController(context, url -> getHostServices().showDocument(url), theme));

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
        } catch (Exception e) {
            // Un error aquí suele ser un fallo en el FXML (fx:id mal escrito, etc.)
            throw new IllegalStateException("No se pudo cargar la interfaz", e);
        }
    }

    /** JavaFX llama a stop() al cerrar la última ventana. */
    @Override
    public void stop() {
        if (context != null) {
            saveWindow();
            context.shutdown();
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

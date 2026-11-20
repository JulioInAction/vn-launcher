package com.vnlauncher.app;

import com.vnlauncher.model.Theme;
import com.vnlauncher.ui.Fonts;
import com.vnlauncher.ui.MainController;
import com.vnlauncher.ui.Views;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Punto de entrada. JavaFX llama a start() en el "hilo de la interfaz" (FX Application Thread):
 * todo lo que toque componentes gráficos debe ejecutarse en ese hilo.
 */
public class Main extends Application {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    private AppContext context;

    @Override
    public void start(Stage stage) {
        Theme theme;
        try {
            context = AppContext.create();
            // Leer el tema guardado ANTES de crear la ventana, para que no se vea un "parpadeo"
            // del tema oscuro al arrancar con el claro
            theme = context.settingsService().theme();
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

            Scene scene = new Scene(root, 1280, 820);
            scene.getStylesheets().add(MainController.class.getResource("styles.css").toExternalForm());

            stage.setTitle("VN Launcher");
            // Icono de la ventana y de la barra de tareas (docs/diseño/logo/vn-launcher-app.png)
            stage.getIcons().add(new Image(
                    MainController.class.getResource("images/vn-launcher-app.png").toExternalForm()));
            stage.setMinWidth(1040);
            stage.setMinHeight(680);
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
            context.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

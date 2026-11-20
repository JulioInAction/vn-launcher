package com.vnlauncher.app;

import com.vnlauncher.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Punto de entrada. JavaFX llama a start() en el "hilo de la interfaz" (FX Application Thread):
 * todo lo que toque componentes gráficos debe ejecutarse en ese hilo.
 */
public class Main extends Application {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    @Override
    public void start(Stage stage) {
        AppContext context;
        try {
            context = AppContext.create();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Error al iniciar", e);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "No se pudo preparar la base de datos:\n" + e.getMessage());
            alert.setHeaderText("Error al iniciar VN Launcher");
            alert.showAndWait();
            return; // sin ventana principal, la app termina sola
        }

        try {
            FXMLLoader loader = new FXMLLoader(MainController.class.getResource("main-view.fxml"));
            // Por defecto FXMLLoader crea el controlador con su constructor vacío. Con una
            // "controller factory" lo creamos nosotros y le pasamos el AppContext por constructor.
            loader.setControllerFactory(type -> new MainController(context));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(MainController.class.getResource("styles.css").toExternalForm());

            stage.setTitle("VN Launcher");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            // Un error aquí suele ser un fallo en el FXML (fx:id mal escrito, etc.)
            throw new IllegalStateException("No se pudo cargar la interfaz", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

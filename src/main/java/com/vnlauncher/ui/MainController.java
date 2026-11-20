package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Controlador de la ventana principal (main-view.fxml): menú lateral + zona central
 * donde se cambian las vistas. Implementa {@link Navigator} para que las demás vistas
 * puedan pedir cambiar de pantalla.
 */
public class MainController implements Navigator {

    /** Límite real de VNDB, el que se muestra al usuario (internamente nos quedamos en 190). */
    private static final int VNDB_LIMIT = 200;

    private final AppContext context;
    /** Abre una URL en el navegador del sistema (viene de Application.getHostServices()). */
    private final Consumer<String> urlOpener;

    // Los campos @FXML los rellena FXMLLoader buscando el fx:id con el mismo nombre en el FXML.
    @FXML private BorderPane root;
    @FXML private Button libraryButton;
    @FXML private Button searchButton;
    @FXML private Label vndbCountLabel;
    @FXML private ProgressBar vndbCountBar;

    // Biblioteca y búsqueda se crean una sola vez y se reutilizan (la búsqueda conserva sus
    // resultados al volver). El detalle de una VN se crea nuevo cada vez.
    private Parent libraryView;
    private LibraryController libraryController;
    private Parent searchView;

    public MainController(AppContext context, Consumer<String> urlOpener) {
        this.context = context;
        this.urlOpener = urlOpener;
    }

    /** FXMLLoader llama a initialize() automáticamente cuando ya ha rellenado los campos @FXML. */
    @FXML
    private void initialize() {
        Tooltip.install(vndbCountLabel, new Tooltip("Datos guardados en " + context.paths().root()));
        startVndbCounter();
        showLibrary();
    }

    /**
     * Actualiza el contador de peticiones cada 2 segundos. Un Timeline es un temporizador de
     * JavaFX: sus KeyFrame se ejecutan en el hilo de la interfaz, así que puede tocar los Label.
     * (recentCount() no bloquea nunca; ver RateLimiter.)
     */
    private void startVndbCounter() {
        Runnable update = () -> {
            int count = context.rateLimiter().recentCount();
            vndbCountLabel.setText(count + " de " + VNDB_LIMIT);
            vndbCountBar.setProgress((double) count / VNDB_LIMIT);
        };
        update.run();
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> update.run()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    @FXML
    @Override
    public void showLibrary() {
        if (libraryView == null) {
            libraryController = new LibraryController(context.libraryService(), context.contentFilter(),
                    context.imageCache(), context.background(), this);
            libraryView = Views.load("library-view.fxml", libraryController);
        }
        libraryController.refresh(); // puede haber VNs nuevas añadidas desde la búsqueda
        root.setCenter(libraryView);
        markActive(libraryButton);
    }

    @FXML
    @Override
    public void showSearch() {
        if (searchView == null) {
            searchView = Views.load("search-view.fxml", new SearchController(
                    context.catalogService(), context.libraryService(), context.background()));
        }
        root.setCenter(searchView);
        markActive(searchButton);
    }

    @Override
    public void showVnDetail(String vnId) {
        root.setCenter(Views.load("vn-detail-view.fxml", new VnDetailController(context, this, urlOpener, vnId)));
        markActive(libraryButton);
    }

    /** Resalta el botón del menú de la sección actual (clase CSS "active"). */
    private void markActive(Button active) {
        for (Button button : new Button[]{libraryButton, searchButton}) {
            button.getStyleClass().remove("active");
        }
        active.getStyleClass().add("active");
    }
}

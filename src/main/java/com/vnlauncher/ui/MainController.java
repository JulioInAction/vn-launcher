package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.sql.SQLException;

/**
 * Controlador de la ventana principal (main-view.fxml): menú lateral + zona central
 * donde se cambian las vistas. Implementa {@link Navigator} para que las demás vistas
 * puedan pedir cambiar de pantalla.
 */
public class MainController implements Navigator {

    private final AppContext context;

    // Los campos @FXML los rellena FXMLLoader buscando el fx:id con el mismo nombre en el FXML.
    @FXML private BorderPane root;
    @FXML private Label statusLabel;
    @FXML private Button libraryButton;
    @FXML private Button searchButton;

    // Biblioteca y búsqueda se crean una sola vez y se reutilizan (la búsqueda conserva sus
    // resultados al volver). El detalle de una VN se crea nuevo cada vez.
    private Parent libraryView;
    private LibraryController libraryController;
    private Parent searchView;

    public MainController(AppContext context) {
        this.context = context;
    }

    /** FXMLLoader llama a initialize() automáticamente cuando ya ha rellenado los campos @FXML. */
    @FXML
    private void initialize() {
        String schema;
        try {
            schema = "v" + context.database().schemaVersion();
        } catch (SQLException e) {
            schema = "error: " + e.getMessage();
        }
        statusLabel.setText("Datos: " + context.paths().root() + "   ·   Esquema " + schema);
        showLibrary();
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
        root.setCenter(Views.load("vn-detail-view.fxml", new VnDetailController(
                context.libraryService(), context.characterService(), context.contentFilter(), context.imageCache(),
                context.background(), this, vnId)));
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

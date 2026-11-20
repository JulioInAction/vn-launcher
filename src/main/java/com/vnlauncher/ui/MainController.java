package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.sql.SQLException;

/**
 * Controlador de la ventana principal (main-view.fxml): menú lateral + zona central
 * donde se cambian las vistas (Buscar, y en la Fase 2 Biblioteca).
 */
public class MainController {

    private final AppContext context;

    // Los campos @FXML los rellena FXMLLoader buscando el fx:id con el mismo nombre en el FXML.
    @FXML private BorderPane root;
    @FXML private Label statusLabel;

    /** La vista de búsqueda se crea una sola vez y se reutiliza (conserva los resultados). */
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
        showSearch();
    }

    @FXML
    private void showSearch() {
        if (searchView == null) {
            searchView = Views.load("search-view.fxml",
                    new SearchController(context.catalogService(), context.background()));
        }
        root.setCenter(searchView);
    }
}

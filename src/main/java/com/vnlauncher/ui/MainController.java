package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.sql.SQLException;

/**
 * Controlador de la ventana principal (main-view.fxml).
 * En la Fase 0 solo muestra información de diagnóstico; las secciones
 * "Biblioteca" y "Buscar" se rellenarán en las fases 1 y 2.
 */
public class MainController {

    private final AppContext context;

    // Los campos @FXML los rellena FXMLLoader buscando el fx:id con el mismo nombre en el FXML.
    @FXML private Label dataDirLabel;
    @FXML private Label schemaLabel;

    public MainController(AppContext context) {
        this.context = context;
    }

    /** FXMLLoader llama a initialize() automáticamente cuando ya ha rellenado los campos @FXML. */
    @FXML
    private void initialize() {
        dataDirLabel.setText(context.paths().root().toString());
        try {
            schemaLabel.setText("v" + context.database().schemaVersion());
        } catch (SQLException e) {
            schemaLabel.setText("error: " + e.getMessage());
        }
    }
}

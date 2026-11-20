package com.vnlauncher.ui;

import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.service.CatalogService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Vista "Buscar en VNDB": caja de texto, lista de resultados y panel de detalle.
 *
 * <p>Regla de oro de JavaFX: el hilo de la interfaz (FX Application Thread) solo debe hacer
 * trabajo rápido. Si hiciéramos la petición HTTP aquí directamente, la ventana se quedaría
 * congelada ("No responde") hasta tener la respuesta. Por eso usamos un {@link Task}:
 * <ul>
 *   <li>{@code call()} se ejecuta en un hilo en segundo plano (NO puede tocar la interfaz).</li>
 *   <li>{@code setOnSucceeded}/{@code setOnFailed} se ejecutan después en el hilo de la
 *       interfaz, así que ahí sí podemos actualizar la lista y las etiquetas.</li>
 * </ul>
 */
public class SearchController {

    private final CatalogService catalog;
    private final ExecutorService background;

    @FXML private TextField queryField;
    @FXML private Button searchButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;
    @FXML private ListView<VisualNovel> resultsList;

    @FXML private VBox detailPane;
    @FXML private Label titleLabel;
    @FXML private Label altTitleLabel;
    @FXML private Label metaLabel;
    @FXML private Label descriptionLabel;

    public SearchController(CatalogService catalog, ExecutorService background) {
        this.catalog = catalog;
        this.background = background;
    }

    @FXML
    private void initialize() {
        // Cada fila de la lista se dibuja con nuestra celda personalizada.
        resultsList.setCellFactory(list -> new SearchResultCell());
        // Al seleccionar un resultado, mostrar su detalle a la derecha.
        resultsList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> showDetail(newValue));
        showDetail(null);
        setBusy(false);
    }

    /** Se llama al pulsar Enter en la caja de texto o el botón Buscar (onAction en el FXML). */
    @FXML
    private void onSearch() {
        String text = queryField.getText();
        if (text == null || text.isBlank()) {
            return; // no gastamos una petición de VNDB en una búsqueda vacía
        }

        Task<List<VisualNovel>> task = new Task<>() {
            @Override
            protected List<VisualNovel> call() throws Exception {
                return catalog.search(text); // en segundo plano
            }
        };
        task.setOnSucceeded(event -> {      // de vuelta en el hilo de la interfaz
            List<VisualNovel> results = task.getValue();
            resultsList.getItems().setAll(results);
            statusLabel.setText(results.isEmpty()
                    ? "Sin resultados para \"" + text + "\""
                    : results.size() + " resultados");
            setBusy(false);
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            statusLabel.setText("Error: " + error.getMessage());
            setBusy(false);
        });

        setBusy(true);
        statusLabel.setText("Buscando \"" + text + "\"...");
        background.submit(task);
    }

    /** Mientras se busca: ruedecita visible y controles desactivados (evita búsquedas dobles). */
    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        queryField.setDisable(busy);
        searchButton.setDisable(busy);
    }

    private void showDetail(VisualNovel vn) {
        detailPane.setVisible(vn != null);
        if (vn == null) {
            return;
        }
        titleLabel.setText(vn.title());
        altTitleLabel.setText(vn.altTitle() == null ? "" : vn.altTitle());
        altTitleLabel.setManaged(vn.altTitle() != null); // managed=false: no ocupa hueco si está vacío
        metaLabel.setText(SearchResultCell.subtitle(vn));
        descriptionLabel.setText(vn.description() == null ? "Sin descripción." : vn.description());
    }
}

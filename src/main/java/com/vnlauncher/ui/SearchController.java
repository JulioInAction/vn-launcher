package com.vnlauncher.ui;

import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.service.CatalogService;
import com.vnlauncher.service.LibraryService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Vista "Buscar en VNDB": caja de texto, lista de resultados y panel de detalle.
 *
 * <p>Regla de oro de JavaFX: el hilo de la interfaz (FX Application Thread) solo debe hacer
 * trabajo rápido. Si hiciéramos la petición HTTP aquí directamente, la ventana se quedaría
 * congelada ("No responde") hasta tener la respuesta. Por eso usamos un Task de JavaFX
 * (envuelto en {@link BackgroundTasks}):
 * <ul>
 *   <li>el trabajo se ejecuta en un hilo en segundo plano (NO puede tocar la interfaz);</li>
 *   <li>el resultado (o el error) se recibe después en el hilo de la interfaz, y ahí sí
 *       podemos actualizar la lista y las etiquetas.</li>
 * </ul>
 */
public class SearchController {

    private final CatalogService catalog;
    private final LibraryService library;
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
    @FXML private Button addButton;
    @FXML private Label descriptionLabel;

    /** Resultado seleccionado ahora mismo (null si ninguno). */
    private VisualNovel selected;

    public SearchController(CatalogService catalog, LibraryService library, ExecutorService background) {
        this.catalog = catalog;
        this.library = library;
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
        setBusy(true);
        statusLabel.setText("Buscando \"" + text + "\"...");

        BackgroundTasks.run(background,
                () -> catalog.search(text),              // en segundo plano
                (List<VisualNovel> results) -> {         // de vuelta en el hilo de la interfaz
                    resultsList.getItems().setAll(results);
                    statusLabel.setText(results.isEmpty()
                            ? "Sin resultados para \"" + text + "\""
                            : results.size() + " resultados");
                    setBusy(false);
                },
                error -> {
                    statusLabel.setText("Error: " + error.getMessage());
                    setBusy(false);
                });
    }

    /** Añade el resultado seleccionado a la biblioteca (guarda datos y descarga la portada). */
    @FXML
    private void onAddToLibrary() {
        VisualNovel vn = selected;
        addButton.setDisable(true);
        addButton.setText("Añadiendo...");
        BackgroundTasks.run(background,
                () -> {
                    library.addToLibrary(vn);
                    return null;
                },
                ignored -> {
                    statusLabel.setText("\"" + vn.title() + "\" añadida a tu biblioteca");
                    if (vn == selected) updateAddButton(vn); // si no ha cambiado la selección
                },
                error -> {
                    statusLabel.setText("Error al añadir: " + error.getMessage());
                    if (vn == selected) updateAddButton(vn);
                });
    }

    /** Mientras se busca: ruedecita visible y controles desactivados (evita búsquedas dobles). */
    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        queryField.setDisable(busy);
        searchButton.setDisable(busy);
    }

    private void showDetail(VisualNovel vn) {
        selected = vn;
        detailPane.setVisible(vn != null);
        if (vn == null) {
            return;
        }
        titleLabel.setText(vn.title());
        altTitleLabel.setText(vn.altTitle() == null ? "" : vn.altTitle());
        altTitleLabel.setManaged(vn.altTitle() != null); // managed=false: no ocupa hueco si está vacío
        metaLabel.setText(SearchResultCell.subtitle(vn));
        descriptionLabel.setText(vn.description() == null ? "Sin descripción." : vn.description());
        updateAddButton(vn);
    }

    /**
     * Consulta si la VN ya está en la biblioteca. Lo hacemos en el hilo de la interfaz porque es
     * una consulta por clave primaria en una BD local: tarda microsegundos. Lo que nunca hacemos
     * aquí son peticiones de red o descargas.
     */
    private void updateAddButton(VisualNovel vn) {
        boolean inLibrary;
        try {
            inLibrary = library.isInLibrary(vn.id());
        } catch (SQLException e) {
            inLibrary = false;
        }
        addButton.setText(inLibrary ? "✓ En tu biblioteca" : "Añadir a la biblioteca");
        addButton.setDisable(inLibrary);
    }
}

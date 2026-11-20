package com.vnlauncher.ui;

import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.LibraryService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Vista "Biblioteca": cuadrícula de portadas de las VNs añadidas, opcionalmente filtrada por
 * estantería (Jugando / Pendientes / Completadas, desde la barra lateral). Todo sale de la base
 * de datos y del disco, así que funciona sin conexión.
 */
public class LibraryController {

    /** Portadas en proporción 2:3 (DISEÑO.md, "Medidas"). */
    private static final double COVER_WIDTH = 176;
    private static final double COVER_HEIGHT = 264;

    private final LibraryService library;
    private final ContentFilter contentFilter;
    private final ImageCacheService images;
    private final ExecutorService background;
    private final Navigator navigator;

    @FXML private Label titleLabel;
    @FXML private Label countLabel;
    @FXML private Hyperlink clearFilterLink;
    @FXML private CheckBox showSensitiveCheck;
    @FXML private Label emptyLabel;
    @FXML private TilePane grid;

    /** Estantería mostrada; null = todas. */
    private PlayStatus shelf;
    /** Las portadas que hay en pantalla, para (des)difuminarlas al marcar la casilla. */
    private final List<CoverView> covers = new ArrayList<>();

    public LibraryController(LibraryService library, ContentFilter contentFilter, ImageCacheService images,
                             ExecutorService background, Navigator navigator) {
        this.library = library;
        this.contentFilter = contentFilter;
        this.images = images;
        this.background = background;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        grid.setPrefTileWidth(COVER_WIDTH);
        // Un "listener" se ejecuta cada vez que cambia la propiedad (aquí, marcar/desmarcar).
        showSensitiveCheck.selectedProperty().addListener((obs, oldValue, selected) ->
                covers.forEach(cover -> cover.setRevealed(selected)));
    }

    /**
     * Muestra la biblioteca (recargándola desde la BD). MainController lo llama cada vez que se
     * entra, porque puede haber VNs nuevas añadidas desde la búsqueda.
     *
     * @param shelf estantería a mostrar, o null para todas
     */
    public void show(PlayStatus shelf) {
        this.shelf = shelf;
        load(true);
    }

    @FXML
    private void onClearFilter() {
        navigator.showLibrary(); // así también se actualiza la barra lateral
    }

    @FXML
    private void onAddVn() {
        navigator.showSearch();
    }

    /**
     * @param repairCovers si true, después de mostrar la biblioteca intenta descargar las portadas
     *                     que falten y, si consigue alguna, vuelve a cargar (esta vez con false,
     *                     para no entrar en un bucle si no hay conexión).
     */
    private void load(boolean repairCovers) {
        BackgroundTasks.run(background, library::findAllWithStatus,
                items -> {
                    render(items);
                    List<LibraryEntry> entries = items.stream().map(LibraryItem::entry).toList();
                    if (repairCovers && library.hasMissingCovers(entries)) {
                        BackgroundTasks.run(background, library::downloadMissingCovers,
                                downloaded -> {
                                    if (downloaded > 0) load(false);
                                },
                                this::showError);
                    }
                },
                this::showError);
    }

    private void render(List<LibraryItem> all) {
        List<LibraryItem> visible = shelf == null ? all
                : all.stream().filter(item -> item.status() == shelf).toList();

        titleLabel.setText(shelf == null ? "Biblioteca" : shelf.shelfName());
        countLabel.setText(visible.size() == 1 ? "1 novela" : visible.size() + " novelas");
        clearFilterLink.setVisible(shelf != null);
        clearFilterLink.setManaged(shelf != null);

        covers.clear();
        grid.getChildren().clear();
        for (LibraryItem item : visible) {
            grid.getChildren().add(createTile(item));
        }

        boolean empty = visible.isEmpty();
        emptyLabel.setText(all.isEmpty()
                ? "Tu biblioteca está vacía. Ve a \"Buscar en VNDB\" y añade alguna novela."
                : "No hay novelas en esta estantería.");
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);
    }

    private VBox createTile(LibraryItem item) {
        VisualNovel vn = item.entry().vn();
        Path file = vn.imagePath() == null ? null : images.resolve(vn.imagePath());
        CoverView cover = new CoverView(file,
                contentFilter.isSensitive(vn.imageSexual(), vn.imageViolence()),
                COVER_WIDTH, COVER_HEIGHT, vn.title());
        cover.setRevealed(showSensitiveCheck.isSelected());
        covers.add(cover);

        Label title = new Label(vn.title());
        title.getStyleClass().add("tile-title");
        title.setMaxWidth(COVER_WIDTH);
        Label developer = new Label(vn.developerNames());
        developer.getStyleClass().addAll("meta", "small");
        developer.setMaxWidth(COVER_WIDTH);

        // Estado (Jugando / Pendiente / Completada) y, si tiene rutas, "Rutas 1/3"
        Label status = new Label(statusLabel(item.status()));
        status.getStyleClass().addAll("pill", pillClass(item.status()));
        HBox statusRow = new HBox(8, status);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        RouteSummary routes = item.routes();
        if (routes.total() > 0) {
            Label routesLabel = new Label("Rutas " + routes.completed() + "/" + routes.total());
            routesLabel.getStyleClass().addAll("meta", "small");
            statusRow.getChildren().add(routesLabel);
        }

        VBox tile = new VBox(4, cover, title, developer, statusRow);
        VBox.setMargin(title, new Insets(8, 0, 0, 0));
        tile.getStyleClass().add("tile");
        tile.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
        return tile;
    }

    /** Texto en singular para la pastilla de cada tarjeta. */
    static String statusLabel(PlayStatus status) {
        return switch (status) {
            case PLAYING -> "Jugando";
            case PENDING -> "Pendiente";
            case COMPLETED -> "Completada";
        };
    }

    /** Misma paleta que los estados de ruta: naranja = jugando, gris = pendiente, azul = completada. */
    static String pillClass(PlayStatus status) {
        return switch (status) {
            case PLAYING -> "pill-in-progress";
            case PENDING -> "pill-pending";
            case COMPLETED -> "pill-completed";
        };
    }

    private void showError(Throwable error) {
        countLabel.setText("Error: " + error.getMessage());
    }
}

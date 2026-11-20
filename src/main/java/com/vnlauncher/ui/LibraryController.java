package com.vnlauncher.ui;

import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.LibraryService;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Vista "Biblioteca": cuadrícula de portadas de las VNs añadidas. Todo sale de la base de datos
 * y del disco, así que funciona sin conexión.
 */
public class LibraryController {

    private static final double COVER_WIDTH = 150;
    private static final double COVER_HEIGHT = 210;

    private final LibraryService library;
    private final ContentFilter contentFilter;
    private final ImageCacheService images;
    private final ExecutorService background;
    private final Navigator navigator;

    @FXML private TilePane grid;
    @FXML private Label countLabel;
    @FXML private Label emptyLabel;
    @FXML private CheckBox showSensitiveCheck;

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
        // Un "listener" se ejecuta cada vez que cambia la propiedad (aquí, marcar/desmarcar).
        showSensitiveCheck.selectedProperty().addListener((obs, oldValue, selected) ->
                covers.forEach(cover -> cover.setRevealed(selected)));
    }

    /** Recarga desde la BD. MainController lo llama cada vez que se muestra la biblioteca. */
    public void refresh() {
        load(true);
    }

    /**
     * @param repairCovers si true, después de mostrar la biblioteca intenta descargar las portadas
     *                     que falten y, si consigue alguna, vuelve a cargar (esta vez con false,
     *                     para no entrar en un bucle si no hay conexión).
     */
    private void load(boolean repairCovers) {
        BackgroundTasks.run(background, library::findAll,
                entries -> {
                    render(entries);
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

    private void render(List<LibraryEntry> entries) {
        covers.clear();
        grid.getChildren().clear();
        for (LibraryEntry entry : entries) {
            grid.getChildren().add(createTile(entry.vn()));
        }
        countLabel.setText(entries.size() == 1 ? "1 novela" : entries.size() + " novelas");
        emptyLabel.setVisible(entries.isEmpty());
        emptyLabel.setManaged(entries.isEmpty());
    }

    private VBox createTile(VisualNovel vn) {
        Path file = vn.imagePath() == null ? null : images.resolve(vn.imagePath());
        CoverView cover = new CoverView(file,
                contentFilter.isSensitive(vn.imageSexual(), vn.imageViolence()),
                COVER_WIDTH, COVER_HEIGHT);
        cover.setRevealed(showSensitiveCheck.isSelected());
        covers.add(cover);

        Label title = new Label(vn.title());
        title.setWrapText(true);
        title.setMaxWidth(COVER_WIDTH);
        title.setMaxHeight(40); // como mucho ~2 líneas; el resto se corta con "..."
        title.getStyleClass().add("tile-title");

        VBox tile = new VBox(6, cover, title);
        tile.getStyleClass().add("tile");
        tile.setCursor(Cursor.HAND);
        tile.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
        return tile;
    }

    private void showError(Throwable error) {
        countLabel.setText("Error: " + error.getMessage());
    }
}

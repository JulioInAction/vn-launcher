package com.vnlauncher.ui;

import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.LibraryService;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Ficha de una VN de la biblioteca. En las próximas fases se le añadirán personajes (3),
 * rutas (4), botón de jugar (5) y tiempo de juego (6).
 */
public class VnDetailController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LibraryService library;
    private final ContentFilter contentFilter;
    private final ImageCacheService images;
    private final ExecutorService background;
    private final Navigator navigator;
    private final String vnId;

    @FXML private StackPane coverBox;
    @FXML private Label titleLabel;
    @FXML private Label altTitleLabel;
    @FXML private Label metaLabel;
    @FXML private Label addedLabel;
    @FXML private Label descriptionLabel;
    @FXML private Button removeButton;

    private VisualNovel vn;

    public VnDetailController(LibraryService library, ContentFilter contentFilter, ImageCacheService images,
                              ExecutorService background, Navigator navigator, String vnId) {
        this.library = library;
        this.contentFilter = contentFilter;
        this.images = images;
        this.background = background;
        this.navigator = navigator;
        this.vnId = vnId;
    }

    @FXML
    private void initialize() {
        removeButton.setDisable(true); // hasta que se cargue la VN
        BackgroundTasks.run(background, () -> library.find(vnId),
                this::render,
                error -> titleLabel.setText("Error: " + error.getMessage()));
    }

    private void render(Optional<LibraryEntry> maybeEntry) {
        if (maybeEntry.isEmpty()) {
            navigator.showLibrary(); // ya no está en la biblioteca
            return;
        }
        LibraryEntry entry = maybeEntry.get();
        vn = entry.vn();

        Path file = vn.imagePath() == null ? null : images.resolve(vn.imagePath());
        CoverView cover = new CoverView(file,
                contentFilter.isSensitive(vn.imageSexual(), vn.imageViolence()), 256, 360);
        if (cover.isSensitive()) {
            // Clic en la portada para mostrarla u ocultarla. Usamos un array de 1 elemento porque
            // una lambda solo puede usar variables locales "efectivamente finales": no podemos
            // reasignar un boolean, pero sí cambiar el contenido del array.
            boolean[] revealed = {false};
            cover.setCursor(Cursor.HAND);
            Tooltip.install(cover, new Tooltip("Clic para mostrar u ocultar la imagen"));
            cover.setOnMouseClicked(event -> {
                revealed[0] = !revealed[0];
                cover.setRevealed(revealed[0]);
            });
        }
        coverBox.getChildren().setAll(cover);

        titleLabel.setText(vn.title());
        altTitleLabel.setText(vn.altTitle() == null ? "" : vn.altTitle());
        altTitleLabel.setManaged(vn.altTitle() != null);
        metaLabel.setText(SearchResultCell.subtitle(vn));
        addedLabel.setText("En tu biblioteca desde el "
                + DATE.format(entry.addedAt().atZone(ZoneId.systemDefault())));
        descriptionLabel.setText(vn.description() == null ? "Sin descripción." : vn.description());
        removeButton.setDisable(false);
    }

    @FXML
    private void onBack() {
        navigator.showLibrary();
    }

    @FXML
    private void onRemove() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Se borrarán también sus rutas y el tiempo de juego registrado.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("¿Quitar \"" + vn.title() + "\" de la biblioteca?");
        // showAndWait bloquea hasta que el usuario responde (es un diálogo modal).
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        removeButton.setDisable(true);
        BackgroundTasks.run(background, () -> {
                    library.remove(vnId);
                    return null; // Callable debe devolver algo; aquí no hay nada útil que devolver
                },
                ignored -> navigator.showLibrary(),
                error -> {
                    removeButton.setDisable(false);
                    new Alert(Alert.AlertType.ERROR, error.getMessage()).showAndWait();
                });
    }
}

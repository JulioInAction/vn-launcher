package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.CoverOption;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.service.CoverService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Galería «Cambiar portada» (docs/design/mockups/cover-gallery-dark.png): la portada principal de
 * VNDB, las imágenes de cada edición y una tarjeta «Elegir archivo…».
 *
 * <p>Al abrirla se hace 1 petición a VNDB (/release) y se descargan las miniaturas, una a una, en
 * segundo plano: la galería sale enseguida y se va llenando. La imagen grande solo se descarga si
 * se pulsa «Usar esta portada».
 */
final class CoverGallery {

    private static final double THUMB_WIDTH = 128;
    private static final double THUMB_HEIGHT = 192;   // 2:3, como las portadas
    private static final double TILE_WIDTH = THUMB_WIDTH + 10; // + marco (borde 2 y relleno 3 por lado)

    private static final String CHECK = "M6 12.5 L10.5 17 L18 8";
    private static final String IMAGE_ICON = "M4 4.5 H16 A1.5 1.5 0 0 1 17.5 6 V14 A1.5 1.5 0 0 1 16 15.5 H4 "
            + "A1.5 1.5 0 0 1 2.5 14 V6 A1.5 1.5 0 0 1 4 4.5 Z M2.5 12.5 L7 9 L10.5 12 L13 10 L17.5 13.5 "
            + "M12.5 7.2 A1.2 1.2 0 1 0 12.51 7.2";

    /** Lo que se puede elegir. "sealed": solo pueden existir estos cuatro tipos. */
    private sealed interface Choice permits VndbMain, VndbImage, CurrentFile, NewFile {
    }

    /** La portada principal de VNDB (= sin portada elegida). */
    private record VndbMain() implements Choice {
    }

    private record VndbImage(CoverOption option) implements Choice {
    }

    /** El archivo propio que ya se usa ahora. */
    private record CurrentFile() implements Choice {
    }

    /** Un archivo propio recién elegido. */
    private record NewFile(Path file) implements Choice {
    }

    private final AppContext ctx;
    private final LibraryEntry entry;
    private final Node owner;
    private final ToggleGroup group = new ToggleGroup();
    /** 6 tarjetas de 138 + 5 huecos de 12 = 888 px: caben en una fila del diálogo, con la barra de desplazamiento. */
    private final FlowPane tiles = new FlowPane(12, 18);
    private final VBox status = new VBox(10);
    private final Button fileTile;
    private ToggleButton newFileTile;
    /** Se pone a true al cerrar: la descarga de miniaturas deja de seguir. */
    private volatile boolean closed;

    private CoverGallery(Node owner, AppContext ctx, LibraryEntry entry) {
        this.owner = owner;
        this.ctx = ctx;
        this.entry = entry;
        this.fileTile = fileTile();
    }

    /**
     * Abre la galería y, si se elige otra portada, la aplica en segundo plano.
     *
     * @param onChanged se ejecuta cuando la portada ya ha cambiado (para volver a pintarla)
     */
    static void show(Node owner, AppContext ctx, LibraryEntry entry, Runnable onChanged) {
        new CoverGallery(owner, ctx, entry).open(onChanged);
    }

    private void open(Runnable onChanged) {
        VisualNovel vn = entry.vn();
        CoverService covers = ctx.coverService();

        // --- Lo que hay sin preguntar a VNDB: la portada principal (ya descargada) y, si se usa
        //     un archivo propio, ese archivo
        Path mainFile = vn.imagePath() == null ? null : ctx.imageCache().resolve(vn.imagePath());
        ToggleButton main = tile(new VndbMain(), "Portada de VNDB", "La principal");
        setImage(main, mainFile != null && ctx.imageCache().isCached(vn.imagePath()) ? mainFile : null,
                ctx.contentFilter().isSensitive(vn.imageSexual(), vn.imageViolence()));
        tiles.getChildren().add(main);
        if (!covers.hasOverride(entry)) {
            markCurrent(main);
        }
        if (CoverService.isCustom(entry) && covers.hasOverride(entry)) {
            ToggleButton custom = tile(new CurrentFile(), "Tu imagen", "Imagen propia");
            setImage(custom, covers.effectiveCover(entry), false);
            tiles.getChildren().add(custom);
            markCurrent(custom);
        }
        tiles.getChildren().add(fileTile);
        // Un ToggleGroup deja "despulsar" la elegida; aquí siempre tiene que haber una
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                group.selectToggle(old);
            }
        });

        ScrollPane scroll = new ScrollPane(tiles);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain-scroll");
        // Dos filas de miniaturas; si hay más, se desplaza
        scroll.setPrefViewportHeight(2 * (THUMB_HEIGHT + 70) + 18);
        tiles.setPadding(new Insets(2));

        ModalDialog dialog = new ModalDialog(owner, 968)
                .titleWithSubtitle("Cambiar portada", vn.title() + " · imágenes de VNDB de cada edición, o una tuya")
                .content(status)
                .content(scroll);
        Button reset = dialog.button("reset", "Volver a la de VNDB", ModalDialog.ButtonKind.LINK);
        reset.setDisable(!covers.hasOverride(entry));
        dialog.button("cancel", "Cancelar", ModalDialog.ButtonKind.SECONDARY);
        Button use = dialog.button("use", "Usar esta portada", ModalDialog.ButtonKind.PRIMARY);
        // Solo se puede confirmar si la elegida no es la que ya se usa
        use.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> group.getSelectedToggle() == null || isCurrent((ToggleButton) group.getSelectedToggle()),
                group.selectedToggleProperty()));

        loadVndbImages();
        String result = dialog.showAndWait();
        closed = true;

        if ("reset".equals(result)) {
            apply(onChanged, () -> covers.resetToVndb(vn.id()));
        } else if ("use".equals(result) && group.getSelectedToggle() != null) {
            Choice choice = (Choice) group.getSelectedToggle().getUserData();
            switch (choice) {
                case VndbMain m -> apply(onChanged, () -> covers.resetToVndb(vn.id()));
                case VndbImage v -> apply(onChanged, () -> covers.useVndbImage(vn.id(), v.option()));
                case NewFile f -> apply(onChanged, () -> covers.useFile(vn.id(), f.file()));
                case CurrentFile c -> { } // ya es la que se usa
            }
        }
    }

    /** Ejecuta el cambio en segundo plano (puede descargar la imagen grande) y avisa al acabar. */
    private void apply(Runnable onChanged, Action action) {
        BackgroundTasks.run(ctx.background(), () -> {
                    action.run();
                    return null;
                },
                ignored -> onChanged.run(),
                error -> {
                    // Sin conexión (no se pudo descargar la imagen): aviso normal. Lo demás no debería pasar.
                    if (ErrorReporter.isExpected(error)) {
                        Dialogs.error(owner, "No se pudo cambiar la portada", error.getMessage());
                    } else {
                        ErrorReporter.report("No se ha podido cambiar la portada.", error);
                    }
                });
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    // =========================================================== imágenes de las ediciones

    /** 1 petición a VNDB; luego, las miniaturas una a una. */
    private void loadVndbImages() {
        Duration wait = ctx.rateLimiter().timeUntilAvailable();
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(22, 22);
        Label loading = new Label(wait.isZero() ? "Cargando las imágenes de las ediciones..."
                : "Esperando al límite de VNDB (" + wait.toMinutesPart() + ":"
                        + String.format("%02d", wait.toSecondsPart()) + ")...");
        loading.getStyleClass().add("muted");
        HBox row = new HBox(10, spinner, loading);
        row.setAlignment(Pos.CENTER_LEFT);
        status.getChildren().setAll(row);

        BackgroundTasks.run(ctx.background(), () -> ctx.coverService().vndbOptions(entry.vn().id()),
                options -> {
                    status.getChildren().clear();
                    if (options.isEmpty()) {
                        Label none = new Label("VNDB no tiene imágenes de las ediciones de esta novela.");
                        none.getStyleClass().add("muted");
                        status.getChildren().add(none);
                    }
                    addOptions(options);
                },
                error -> status.getChildren().setAll(Notice.vndbError(error, this::loadVndbImages)));
    }

    private void addOptions(List<CoverOption> options) {
        List<ToggleButton> added = new java.util.ArrayList<>();
        for (CoverOption option : options) {
            String name = option.releaseTitle() == null ? "Edición" : option.releaseTitle();
            ToggleButton tile = tile(new VndbImage(option), name, option.subtitle());
            Tooltip.install(tile, new Tooltip(name + "\n" + option.subtitle()));
            if (CoverService.isCurrent(entry, option)) {
                markCurrent(tile);
            }
            added.add(tile);
        }
        // Detrás de todo: «Portada de VNDB» y «Elegir archivo…» van siempre al principio. (En el
        // diseño la tarjeta del archivo va la última, pero con 40 imágenes quedaría siete filas más abajo.)
        tiles.getChildren().addAll(added);

        // Miniaturas en segundo plano, de una en una: aparecen según llegan
        // execute y no submit: con submit, una excepción se guardaría en un Future que nadie mira; con
        // execute llega al manejador global de errores (ErrorReporter)
        ctx.background().execute(() -> {
            for (ToggleButton tile : added) {
                if (closed) {
                    return;
                }
                CoverOption option = ((VndbImage) tile.getUserData()).option();
                try {
                    Path file = ctx.coverService().thumbnail(option);
                    boolean sensitive = ctx.contentFilter().isSensitive(option.sexual(), option.violence());
                    Platform.runLater(() -> setImage(tile, file, sensitive));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    // Sin esa miniatura se queda el hueco; las demás siguen
                    System.getLogger(CoverGallery.class.getName())
                            .log(System.Logger.Level.WARNING, "Miniatura no descargada: " + option.imageId(), e);
                }
            }
        });
    }

    // =========================================================== tarjetas

    /**
     * Tarjeta seleccionable: marco con la imagen (y la marca de elegida), nombre y detalle debajo.
     * Es un ToggleButton del grupo: la selección, el foco y el teclado vienen hechos.
     */
    private ToggleButton tile(Choice choice, String name, String detail) {
        StackPane image = new StackPane(new CoverView((String) null, false, THUMB_WIDTH, THUMB_HEIGHT, null));
        image.getStyleClass().add("gallery-image");

        SVGPath mark = new SVGPath();
        mark.setContent(CHECK);
        mark.getStyleClass().add("gallery-check-mark");
        StackPane check = new StackPane(new Group(mark));
        check.getStyleClass().add("gallery-check");
        StackPane.setAlignment(check, Pos.TOP_LEFT);
        StackPane.setMargin(check, new Insets(6));

        StackPane frame = new StackPane(image, check);
        frame.getStyleClass().add("gallery-frame");

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("gallery-name");
        nameLabel.setMaxWidth(TILE_WIDTH);
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("gallery-detail");
        detailLabel.setMaxWidth(TILE_WIDTH);
        VBox box = new VBox(4, frame, nameLabel, detailLabel);
        VBox.setMargin(nameLabel, new Insets(4, 0, 0, 2));
        VBox.setMargin(detailLabel, new Insets(0, 0, 0, 2));

        ToggleButton tile = new ToggleButton();
        tile.getStyleClass().setAll("gallery-tile"); // sin el estilo de botón: solo el marco cambia
        tile.setGraphic(box);
        tile.setUserData(choice);
        tile.setToggleGroup(group);
        check.visibleProperty().bind(tile.selectedProperty());
        // Una imagen sensible sale difuminada; si el usuario la elige (clic o teclado: el evento de
        // acción), se muestra, porque hay que ver qué se elige. La que ya viene elegida al abrir
        // (la actual) no se destapa sola. Al elegir otra, se vuelve a difuminar.
        tile.setOnAction(event -> {
            if (tile.isSelected() && image.getChildren().getFirst() instanceof CoverView cover) {
                cover.setRevealed(true);
            }
        });
        tile.selectedProperty().addListener((obs, was, selected) -> {
            if (!selected && image.getChildren().getFirst() instanceof CoverView cover) {
                cover.setRevealed(false);
            }
        });
        return tile;
    }

    /** Pone la miniatura (o deja el hueco si file es null). */
    private void setImage(ToggleButton tile, Path file, boolean sensitive) {
        StackPane image = imageOf(tile);
        CoverView cover = new CoverView(file, sensitive, THUMB_WIDTH, THUMB_HEIGHT);
        // La etiqueta «Actual», si la hay, se queda encima
        Node current = image.getChildren().size() > 1 ? image.getChildren().get(1) : null;
        image.getChildren().setAll(cover);
        if (current != null) {
            image.getChildren().add(current);
        }
    }

    private static StackPane imageOf(ToggleButton tile) {
        VBox box = (VBox) tile.getGraphic();
        StackPane frame = (StackPane) box.getChildren().getFirst();
        return (StackPane) frame.getChildren().getFirst();
    }

    /** Etiqueta «Actual» y elegida al abrir. */
    private void markCurrent(ToggleButton tile) {
        Label current = new Label("Actual");
        current.getStyleClass().add("gallery-current");
        StackPane.setAlignment(current, Pos.BOTTOM_LEFT);
        StackPane.setMargin(current, new Insets(6));
        imageOf(tile).getChildren().add(current);
        tile.getProperties().put("current", true);
        group.selectToggle(tile);
    }

    private static boolean isCurrent(ToggleButton tile) {
        return Boolean.TRUE.equals(tile.getProperties().get("current"));
    }

    /** «Elegir archivo…»: abre el selector; el archivo elegido aparece como una tarjeta más, ya elegida. */
    private Button fileTile() {
        SVGPath icon = new SVGPath();
        icon.setContent(IMAGE_ICON);
        icon.getStyleClass().add("icon-stroke");
        Label text = new Label("Elegir archivo...");
        text.getStyleClass().add("gallery-file-text");
        VBox inside = new VBox(8, new Group(icon), text);
        inside.setAlignment(Pos.CENTER);
        StackPane frame = new StackPane(inside);
        frame.getStyleClass().add("gallery-file");
        frame.setMinSize(THUMB_WIDTH, THUMB_HEIGHT);
        frame.setPrefSize(THUMB_WIDTH, THUMB_HEIGHT);
        frame.setMaxSize(THUMB_WIDTH, THUMB_HEIGHT);
        StackPane framed = new StackPane(frame);
        framed.setPadding(new Insets(5)); // mismo hueco que el marco de las demás tarjetas

        Label name = new Label("Imagen propia");
        name.getStyleClass().add("gallery-name");
        Label detail = new Label("PNG o JPG");
        detail.getStyleClass().add("gallery-detail");
        VBox box = new VBox(4, framed, name, detail);
        VBox.setMargin(name, new Insets(4, 0, 0, 2));
        VBox.setMargin(detail, new Insets(0, 0, 0, 2));

        Button button = new Button();
        button.getStyleClass().setAll("gallery-tile");
        button.setGraphic(box);
        button.setOnAction(event -> chooseFile());
        return button;
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elige una imagen para la portada");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes (PNG o JPG)", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File chosen = chooser.showOpenDialog(fileTile.getScene().getWindow());
        if (chosen == null) {
            return;
        }
        if (newFileTile != null) {
            tiles.getChildren().remove(newFileTile);
            group.getToggles().remove(newFileTile);
        }
        newFileTile = tile(new NewFile(chosen.toPath()), "Tu imagen", chosen.getName());
        setImage(newFileTile, chosen.toPath(), false);
        tiles.getChildren().add(tiles.getChildren().indexOf(fileTile), newFileTile);
        group.selectToggle(newFileTile);
    }
}

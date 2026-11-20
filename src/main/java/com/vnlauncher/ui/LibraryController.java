package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.CurrentRoute;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.service.LibraryService;
import com.vnlauncher.service.LibrarySort;
import com.vnlauncher.util.PlayTime;
import com.vnlauncher.util.RelativeTime;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Vista "Biblioteca": franja "Continuar" con la última VN jugada, filtros por estado con sus
 * contadores, orden y cuadrícula de portadas. Todo sale de la base de datos y del disco, así que
 * funciona sin conexión.
 */
public class LibraryController implements PlaytimeAware {

    /** Portadas en proporción 2:3 (DISEÑO.md, "Medidas"). */
    private static final double COVER_WIDTH = 176;
    private static final double COVER_HEIGHT = 264;

    /** Lo que carga la tarea de fondo: la biblioteca y la ruta en curso de la última VN jugada. */
    private record LibraryData(List<LibraryItem> items, Optional<CurrentRoute> currentRoute) {
    }

    private final AppContext ctx;
    private final Navigator navigator;

    @FXML private Label countLabel;
    @FXML private HBox continueCard;
    @FXML private HBox chipsBox;
    @FXML private CheckBox showSensitiveCheck;
    @FXML private ComboBox<LibrarySort> sortBox;
    @FXML private Label emptyLabel;
    @FXML private TilePane grid;

    /** Estantería mostrada; null = todas. */
    private PlayStatus shelf;
    private LibraryData data = new LibraryData(List.of(), Optional.empty());
    /** Las portadas que hay en pantalla, para (des)difuminarlas al marcar la casilla. */
    private final List<CoverView> covers = new ArrayList<>();

    public LibraryController(AppContext ctx, Navigator navigator) {
        this.ctx = ctx;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        grid.setPrefTileWidth(COVER_WIDTH);
        // Un "listener" se ejecuta cada vez que cambia la propiedad (aquí, marcar/desmarcar).
        showSensitiveCheck.selectedProperty().addListener((obs, oldValue, selected) ->
                covers.forEach(cover -> cover.setRevealed(selected)));

        sortBox.getItems().setAll(LibrarySort.values());
        sortBox.setCellFactory(list -> new SortCell());
        sortBox.setButtonCell(new SortCell());
        sortBox.setValue(LibrarySort.LAST_PLAYED);
        sortBox.valueProperty().addListener((obs, oldValue, sort) -> render());
    }

    /**
     * Muestra la biblioteca (recargándola desde la BD). MainController lo llama cada vez que se
     * entra, porque puede haber VNs nuevas añadidas desde la búsqueda.
     *
     * @param shelf estantería a mostrar (desde la barra lateral), o null para todas
     */
    public void show(PlayStatus shelf) {
        this.shelf = shelf;
        load(true);
    }

    /** Un juego se ha abierto o cerrado: cambian horas, estados y la franja "Continuar". */
    @Override
    public void playtimeChanged(String vnId) {
        load(false);
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
        LibraryService library = ctx.libraryService();
        BackgroundTasks.run(ctx.background(), () -> {
                    List<LibraryItem> items = library.findAllWithStatus();
                    Optional<CurrentRoute> current = Optional.empty();
                    List<LibraryEntry> recent = LibraryService.recentlyPlayed(items, 1);
                    if (!recent.isEmpty()) {
                        current = library.currentRoute(recent.getFirst().vn().id());
                    }
                    return new LibraryData(items, current);
                },
                loaded -> {
                    data = loaded;
                    render();
                    List<LibraryEntry> entries = loaded.items().stream().map(LibraryItem::entry).toList();
                    if (repairCovers && library.hasMissingCovers(entries)) {
                        BackgroundTasks.run(ctx.background(), library::downloadMissingCovers,
                                downloaded -> {
                                    if (downloaded > 0) load(false);
                                },
                                this::showError);
                    }
                },
                this::showError);
    }

    // =========================================================== dibujo

    private void render() {
        List<LibraryItem> all = data.items();
        countLabel.setText(all.size() == 1 ? "1 novela" : all.size() + " novelas");
        renderContinue();
        renderChips();

        List<LibraryItem> visible = all.stream()
                .filter(item -> shelf == null || item.status() == shelf)
                .sorted(sortBox.getValue().comparator())
                .toList();
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

    /** Franja "Continuar": la VN jugada más recientemente, su ruta en curso y el botón Jugar. */
    private void renderContinue() {
        List<LibraryEntry> recent = LibraryService.recentlyPlayed(data.items(), 1);
        continueCard.getChildren().clear();
        continueCard.setVisible(!recent.isEmpty());
        continueCard.setManaged(!recent.isEmpty());
        if (recent.isEmpty()) {
            return;
        }
        LibraryEntry entry = recent.getFirst();
        VisualNovel vn = entry.vn();

        CoverView cover = new CoverView(coverFile(vn), isSensitive(vn), 62, 93, vn.title());
        cover.setRevealed(showSensitiveCheck.isSelected());

        Label caps = new Label("CONTINUAR");
        caps.getStyleClass().addAll("caps", "continue-caps");
        Label title = new Label(vn.title());
        title.getStyleClass().add("card-title");
        // "Ruta Unlimited Blade Works · Rin Tohsaka · última partida hace 2 días"
        List<String> parts = new ArrayList<>();
        data.currentRoute().ifPresent(route -> {
            parts.add("Ruta " + route.routeName());
            if (route.heroineName() != null && !route.heroineName().equals(route.routeName())) {
                parts.add(route.heroineName());
            }
        });
        boolean running = ctx.launchService().isRunning(vn.id());
        parts.add(running ? "jugando ahora"
                : "última partida " + RelativeTime.format(entry.lastPlayedAt(), Instant.now(), ZoneId.systemDefault()));
        Label meta = new Label(String.join(" · ", parts));
        meta.getStyleClass().add("text2");
        VBox texts = new VBox(4, caps, title, meta);
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0);

        Button play = new Button(running ? "Jugando..." : "▶  Jugar");
        play.getStyleClass().addAll("primary-button", "big-button");
        play.setDisable(running);
        play.setOnAction(event -> {
            play.setDisable(true);
            play.setText("Lanzando...");
            new LaunchFlow(ctx, play).play(vn.id(), vn.title(),
                    () -> {
                        load(false);
                        navigator.refreshSidebar();
                    },
                    this::render); // si no se lanzó, volver a dibujar el botón como estaba
        });

        continueCard.getChildren().addAll(cover, texts);
        if (entry.useLocaleEmulator()) {
            Label le = new Label("Locale Emulator");
            le.getStyleClass().add("badge");
            continueCard.getChildren().add(le);
        }
        continueCard.getChildren().add(play);
        // Clic en la franja (fuera del botón) = abrir la ficha
        cover.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
        texts.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
    }

    /** Filtros: Todas 8 · Jugando 2 · Pendientes 3 · Completadas 3. */
    private void renderChips() {
        chipsBox.getChildren().clear();
        chipsBox.getChildren().add(chip("Todas", data.items().size(), null));
        for (PlayStatus status : PlayStatus.values()) {
            long n = data.items().stream().filter(i -> i.status() == status).count();
            chipsBox.getChildren().add(chip(status.shelfName(), n, status));
        }
    }

    private Button chip(String name, long count, PlayStatus status) {
        Label countLabel = new Label(String.valueOf(count));
        countLabel.getStyleClass().add("chip-count");
        Button chip = new Button(name, countLabel);
        chip.setContentDisplay(ContentDisplay.RIGHT); // número a la derecha
        chip.getStyleClass().add("chip");
        // Sin esto, si falta sitio JavaFX encoge el botón y corta el texto con "..."
        chip.setMinWidth(Region.USE_PREF_SIZE);
        if (status == shelf) {
            chip.getStyleClass().add("active");
        }
        chip.setOnAction(event -> {
            shelf = status;
            render();
        });
        return chip;
    }

    private VBox createTile(LibraryItem item) {
        VisualNovel vn = item.entry().vn();
        CoverView cover = new CoverView(coverFile(vn), isSensitive(vn), COVER_WIDTH, COVER_HEIGHT, vn.title());
        cover.setRevealed(showSensitiveCheck.isSelected());
        covers.add(cover);

        Label title = new Label(vn.title());
        title.getStyleClass().add("tile-title");
        title.setMaxWidth(COVER_WIDTH);
        Label developer = new Label(vn.developerNames());
        developer.getStyleClass().addAll("meta", "small");
        developer.setMaxWidth(COVER_WIDTH);

        // Estado (Jugando / Pendiente / Completada) y "Rutas 1/3 · 36 h" o "Sin jugar"
        Label status = new Label(statusLabel(item.status()));
        status.getStyleClass().addAll("pill", pillClass(item.status()));
        status.setMinWidth(Region.USE_PREF_SIZE); // la pastilla nunca se corta; el texto de al lado sí
        List<String> details = new ArrayList<>();
        RouteSummary routes = item.routes();
        if (routes.total() > 0) {
            details.add("Rutas " + routes.completed() + "/" + routes.total());
        }
        details.add(PlayTime.formatShort(item.playSeconds()));
        Label detailLabel = new Label(String.join(" · ", details));
        detailLabel.getStyleClass().addAll("meta", "small");
        HBox statusRow = new HBox(8, status, detailLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        VBox tile = new VBox(4, cover, title, developer, statusRow);
        VBox.setMargin(title, new Insets(8, 0, 0, 0));
        tile.getStyleClass().add("tile");
        tile.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
        return tile;
    }

    private Path coverFile(VisualNovel vn) {
        return vn.imagePath() == null ? null : ctx.imageCache().resolve(vn.imagePath());
    }

    private boolean isSensitive(VisualNovel vn) {
        return ctx.contentFilter().isSensitive(vn.imageSexual(), vn.imageViolence());
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

    /** Celda del desplegable "Ordenar por": muestra "Último jugado" en vez de LAST_PLAYED. */
    private static class SortCell extends ListCell<LibrarySort> {
        @Override
        protected void updateItem(LibrarySort sort, boolean empty) {
            super.updateItem(sort, empty);
            setText(empty || sort == null ? null : sort.label());
        }
    }
}

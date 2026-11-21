package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.CurrentRoute;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnTag;
import com.vnlauncher.service.LibraryService;
import com.vnlauncher.service.LibrarySort;
import com.vnlauncher.util.PlayTime;
import com.vnlauncher.util.RelativeTime;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Vista "Biblioteca": franja "Continuar" con la última VN jugada, filtros por estado con sus
 * contadores, filtro por etiqueta (desde la ficha), orden y cuadrícula de portadas. Todo sale de la
 * base de datos y del disco, así que funciona sin conexión.
 */
public class LibraryController implements PlaytimeAware {

    private static final System.Logger LOG = System.getLogger(LibraryController.class.getName());

    /** Portadas en proporción 2:3 (DISEÑO.md, "Medidas"). */
    private static final double COVER_WIDTH = 176;
    private static final double COVER_HEIGHT = 264;

    /**
     * Lo que carga la tarea de fondo: la biblioteca, la ruta en curso de la última VN jugada y, si
     * se filtra por etiqueta, qué VNs la tienen y cuántas aún no tienen sus etiquetas guardadas.
     */
    private record LibraryData(List<LibraryItem> items, Optional<CurrentRoute> currentRoute,
                               Set<String> withTag, int withoutTags) {
    }

    private final AppContext ctx;
    private final Navigator navigator;

    @FXML private Label countLabel;
    @FXML private Label coversLabel;
    @FXML private ProgressBar coversBar;
    @FXML private Button addButton;
    @FXML private VBox emptyBox;
    @FXML private HBox filtersRow;
    @FXML private HBox tagFilterRow;
    @FXML private HBox continueCard;
    @FXML private FlowPane chipsBox;
    @FXML private CheckBox showSensitiveCheck;
    @FXML private ComboBox<LibrarySort> sortBox;
    @FXML private Label emptyLabel;
    @FXML private TilePane grid;

    /** Estantería mostrada; null = todas. */
    private PlayStatus shelf;
    /** Etiqueta por la que se filtra (se combina con la estantería); null = ninguna. */
    private VnTag tag;
    private LibraryData data = new LibraryData(List.of(), Optional.empty(), Set.of(), 0);
    /** Las portadas que hay en pantalla, para (des)difuminarlas al marcar la casilla. */
    private final List<CoverView> covers = new ArrayList<>();

    public LibraryController(AppContext ctx, Navigator navigator) {
        this.ctx = ctx;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        showCoversProgress(false);
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
        this.tag = null;
        load(true);
    }

    /** Muestra solo las VNs con esta etiqueta (todas las estanterías). */
    public void showTag(VnTag tag) {
        this.shelf = null;
        this.tag = tag;
        load(true);
    }

    /** Vuelve a cargar sin cambiar los filtros (al volver de una ficha). */
    public void reload() {
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
        VnTag filter = tag; // la lambda corre en otro hilo: mejor no leer el campo desde allí
        BackgroundTasks.run(ctx.background(), () -> {
                    List<LibraryItem> items = library.findAllWithStatus();
                    Optional<CurrentRoute> current = Optional.empty();
                    List<LibraryEntry> recent = LibraryService.recentlyPlayed(items, 1);
                    if (!recent.isEmpty()) {
                        current = library.currentRoute(recent.getFirst().vn().id());
                    }
                    Set<String> withTag = Set.of();
                    int withoutTags = 0;
                    if (filter != null) {
                        withTag = ctx.vnDetailsService().vnIdsWithTag(filter.id());
                        withoutTags = ctx.vnDetailsService().countLibraryWithoutTags();
                    }
                    return new LibraryData(items, current, withTag, withoutTags);
                },
                loaded -> {
                    data = loaded;
                    render();
                    List<LibraryEntry> entries = loaded.items().stream().map(LibraryItem::entry).toList();
                    if (repairCovers && library.hasMissingCovers(entries)) {
                        downloadCovers();
                    }
                },
                "No se ha podido cargar la biblioteca.");
    }

    /**
     * Descarga las portadas que falten mostrando el progreso ("Descargando portadas · 3 de 8").
     *
     * <p>Aquí no basta con BackgroundTasks: hace falta informar del avance DURANTE el trabajo. Un
     * Task de JavaFX tiene updateMessage/updateProgress, que se pueden llamar desde el hilo de
     * fondo; enlazando (bind) la etiqueta y la barra a messageProperty/progressProperty, JavaFX
     * las actualiza en el hilo de la interfaz él solo.
     */
    private void downloadCovers() {
        LibraryService library = ctx.libraryService();
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return library.downloadMissingCovers((done, total) -> {
                    updateMessage("Descargando portadas · " + done + " de " + total);
                    updateProgress(done, total);
                });
            }
        };
        coversLabel.textProperty().bind(task.messageProperty());
        coversBar.progressProperty().bind(task.progressProperty());
        showCoversProgress(true);
        task.setOnSucceeded(event -> {
            showCoversProgress(false);
            if (task.getValue() > 0) {
                load(false);
                navigator.refreshSidebar(); // las miniaturas de "Recientes" también usan estas portadas
            }
        });
        task.setOnFailed(event -> {
            // Sin conexión, normalmente: las que falten se reintentan la próxima vez
            showCoversProgress(false);
            LOG.log(System.Logger.Level.WARNING, "No se pudieron descargar las portadas", task.getException());
        });
        ctx.background().submit(task);
    }

    private void showCoversProgress(boolean visible) {
        if (!visible) {
            coversLabel.textProperty().unbind();
            coversBar.progressProperty().unbind();
        }
        coversLabel.setVisible(visible);
        coversLabel.setManaged(visible);
        coversBar.setVisible(visible);
        coversBar.setManaged(visible);
    }

    // =========================================================== dibujo

    private void render() {
        List<LibraryItem> all = data.items();
        countLabel.setText(all.size() == 1 ? "1 novela" : all.size() + " novelas");
        renderEmptyLibrary(all.isEmpty());
        if (all.isEmpty()) {
            return;
        }
        renderContinue();
        renderTagFilter();
        // Primero la etiqueta y después la estantería: así los contadores de las pastillas
        // (Todas 3 · Jugando 1...) cuentan solo las VNs con esa etiqueta
        List<LibraryItem> tagged = all.stream()
                .filter(item -> tag == null || data.withTag().contains(item.entry().vn().id()))
                .toList();
        renderChips(tagged);

        List<LibraryItem> visible = tagged.stream()
                .filter(item -> shelf == null || item.status() == shelf)
                .sorted(sortBox.getValue().comparator())
                .toList();
        covers.clear();
        grid.getChildren().clear();
        for (LibraryItem item : visible) {
            grid.getChildren().add(createTile(item));
        }

        boolean empty = visible.isEmpty();
        emptyLabel.setText(tag == null ? "No hay novelas en esta estantería."
                : "Ninguna novela " + (shelf == null ? "de tu biblioteca" : "de esta estantería")
                        + " tiene la etiqueta «" + tag.name() + "».");
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);
    }

    /**
     * Filtro por etiqueta activo: un chip «Etiqueta: Drama ✕» que lo quita al pulsarlo y, si hay
     * VNs cuya ficha nunca se ha abierto (sus etiquetas aún no se han descargado), un aviso.
     */
    private void renderTagFilter() {
        tagFilterRow.getChildren().clear();
        boolean active = tag != null;
        tagFilterRow.setVisible(active);
        tagFilterRow.setManaged(active);
        if (!active) {
            return;
        }
        Button chip = new Button("Etiqueta: " + tag.name() + "   ✕");
        chip.getStyleClass().addAll("chip", "active", "tag-filter");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setTooltip(new Tooltip("Quitar el filtro"));
        chip.setOnAction(event -> {
            tag = null;
            render(); // no hace falta volver a la base de datos: sin etiqueta no se usa withTag
        });
        tagFilterRow.getChildren().add(chip);
        if (data.withoutTags() > 0) {
            Label note = new Label((data.withoutTags() == 1 ? "1 novela aún no tiene"
                    : data.withoutTags() + " novelas aún no tienen")
                    + " sus etiquetas: se descargan al abrir su ficha.");
            note.getStyleClass().add("meta");
            note.setWrapText(true);
            tagFilterRow.getChildren().add(note);
        }
    }

    /** Con la biblioteca vacía, en vez de franja, filtros y cuadrícula: explicación + botón. */
    private void renderEmptyLibrary(boolean empty) {
        emptyBox.getChildren().clear();
        if (empty) {
            Button search = new Button("Buscar en VNDB");
            search.getStyleClass().addAll("primary-button", "big-button");
            search.setOnAction(event -> navigator.showSearch());
            emptyBox.getChildren().add(EmptyState.create(EmptyState.booksIcon(),
                    "Tu biblioteca está vacía", search,
                    "Busca una novela en VNDB y añádela. Los datos se guardan en tu ordenador, así que "
                            + "después funciona sin conexión."));
            continueCard.setVisible(false);
            continueCard.setManaged(false);
            grid.getChildren().clear();
            emptyLabel.setVisible(false);
            emptyLabel.setManaged(false);
        }
        emptyBox.setVisible(empty);
        emptyBox.setManaged(empty);
        for (Node n : List.of(filtersRow, showSensitiveCheck, addButton, countLabel)) {
            n.setVisible(!empty);
            n.setManaged(!empty);
        }
        if (empty) {
            tagFilterRow.setVisible(false);
            tagFilterRow.setManaged(false);
        }
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

        CoverView cover = new CoverView(Covers.file(ctx, entry), Covers.sensitive(ctx, entry), 62, 93, vn.title());
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
        meta.setWrapText(true); // con la ventana estrecha, en dos líneas mejor que cortado
        VBox texts = new VBox(4, caps, title, meta);
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0);

        Button play = new Button(running ? "Jugando..." : "▶  Jugar");
        play.getStyleClass().addAll("primary-button", "big-button");
        play.setMinWidth(Region.USE_PREF_SIZE); // el botón nunca se corta ("Ju..."); cede el texto
        play.setDisable(running);
        play.setOnAction(event -> {
            play.setDisable(true);
            play.setText("Lanzando...");
            new LaunchFlow(ctx, navigator, play).play(vn.id(), vn.title(),
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
            le.setMinWidth(Region.USE_PREF_SIZE);
            continueCard.getChildren().add(le);
        }
        continueCard.getChildren().add(play);
        // Clic en la franja (fuera del botón) = abrir la ficha
        cover.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
        texts.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
    }

    /** Filtros: Todas 8 · Jugando 2 · Pendientes 3 · Completadas 3 (de las VNs que se cuentan). */
    private void renderChips(List<LibraryItem> items) {
        chipsBox.getChildren().clear();
        chipsBox.getChildren().add(chip("Todas", items.size(), null));
        for (PlayStatus status : PlayStatus.values()) {
            long n = items.stream().filter(i -> i.status() == status).count();
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
        LibraryEntry entry = item.entry();
        VisualNovel vn = entry.vn();
        // Portada de VNDB aún sin descargar (y ninguna elegida): hueco vacío en vez de iniciales
        CoverView cover = ctx.libraryService().coverPending(vn) && !ctx.coverService().hasOverride(entry)
                ? new CoverView((String) null, false, COVER_WIDTH, COVER_HEIGHT, null)
                : new CoverView(Covers.file(ctx, entry), Covers.sensitive(ctx, entry), COVER_WIDTH, COVER_HEIGHT,
                vn.title());
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
        // FlowPane y no HBox: si "Rutas 1/3 · 36 h" no cabe al lado de la pastilla, pasa a la
        // línea de abajo en vez de cortarse con "..."
        FlowPane statusRow = new FlowPane(8, 4, status, detailLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPrefWrapLength(COVER_WIDTH);

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

    /** Celda del desplegable "Ordenar por": muestra "Último jugado" en vez de LAST_PLAYED. */
    private static class SortCell extends ListCell<LibrarySort> {
        @Override
        protected void updateItem(LibrarySort sort, boolean empty) {
            super.updateItem(sort, empty);
            setText(empty || sort == null ? null : sort.label());
        }
    }
}

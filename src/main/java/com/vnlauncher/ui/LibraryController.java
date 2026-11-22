package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.CurrentRoute;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnTag;
import com.vnlauncher.service.LibraryQuery;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Vista "Biblioteca": franja "Continuar" con la última VN jugada, los filtros activos (el estado o
 * la estantería elegidos en la barra lateral y la etiqueta pulsada en una ficha, cada uno con su ✕),
 * el orden y la cuadrícula de portadas. Los filtros van en un {@link LibraryQuery}. Todo sale de la
 * base de datos y del disco, así que funciona sin conexión.
 */
public class LibraryController implements PlaytimeAware, ShelvesAware {

    private static final System.Logger LOG = System.getLogger(LibraryController.class.getName());

    /** Portadas en proporción 2:3 (DISEÑO.md, "Medidas"). */
    private static final double COVER_WIDTH = 176;
    private static final double COVER_HEIGHT = 264;

    /** MessageFormat con {@code choice}: «1 novela», «9 novelas». */
    private static final String COUNT = "{0,choice,0#0 novelas|1#1 novela|1<{0,number,integer} novelas}";
    private static final String MISSING_TOOLTIP = "No se encuentra el ejecutable. Ábrelo en su ficha para buscarlo.";
    private static final String STATUS_FILTER = "Estado: %s   ✕";
    private static final String SHELF_FILTER = "Estantería: %s   ✕";
    private static final String EMPTY_SHELF = "La estantería «%s» está vacía. Añade novelas desde su ficha, "
            + "con «+ Añadir a estantería».";
    private static final String NONE_IN_SHELF_WITH_TAG = "Ninguna novela de «%s» tiene la etiqueta «%s».";
    private static final String TAG_FILTER = "Etiqueta: %s   ✕";
    private static final String REMOVE_FILTER = "Quitar el filtro";
    private static final String NONE_WITH_STATUS = "No hay ninguna novela con el estado «%s».";
    private static final String NONE_WITH_TAG = "Ninguna novela de tu biblioteca tiene la etiqueta «%s».";
    private static final String NONE_WITH_BOTH = "Ninguna novela con el estado «%s» tiene la etiqueta «%s».";
    private static final String WITHOUT_TAGS = "{0,choice,1#1 novela aún no tiene|1<{0,number,integer} novelas "
            + "aún no tienen} sus etiquetas: se descargan al abrir su ficha.";

    /**
     * Lo que carga la tarea de fondo: la biblioteca, la ruta en curso de la última VN jugada; si se
     * filtra por estantería, la estantería tal y como está ahora (vacío si ya no existe) y sus VNs;
     * y si se filtra por etiqueta, qué VNs la tienen y cuántas aún no tienen sus etiquetas guardadas.
     */
    private record LibraryData(List<LibraryItem> items, Optional<CurrentRoute> currentRoute,
                               Optional<Shelf> shelf, Set<String> inShelf, Set<String> withTag, int withoutTags) {
    }

    private final AppContext ctx;
    private final Navigator navigator;
    private final Consumer<String> urlOpener;

    @FXML private Label countLabel;
    @FXML private Label coversLabel;
    @FXML private ProgressBar coversBar;
    @FXML private Button addButton;
    @FXML private VBox emptyBox;
    @FXML private HBox filtersRow;
    @FXML private FlowPane activeFiltersBox;
    @FXML private Label filterNote;
    @FXML private HBox continueCard;
    @FXML private CheckBox showSensitiveCheck;
    @FXML private ComboBox<LibrarySort> sortBox;
    @FXML private Label emptyLabel;
    @FXML private TilePane grid;

    /** Los filtros: estado o estantería, etiqueta y texto (este, en la fase 11). */
    private LibraryQuery query = LibraryQuery.ALL;
    /** Para que la barra lateral resalte el estado o la estantería filtrados (lo conecta MainController). */
    private Consumer<LibraryQuery> onQueryChanged = q -> { };
    private LibraryData data = new LibraryData(List.of(), Optional.empty(), Optional.empty(), Set.of(), Set.of(), 0);
    /** Las portadas que hay en pantalla, para (des)difuminarlas al marcar la casilla. */
    private final List<CoverView> covers = new ArrayList<>();

    /** @param urlOpener abre una URL o una carpeta (file:/...) con el programa del sistema */
    public LibraryController(AppContext ctx, Navigator navigator, Consumer<String> urlOpener) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.urlOpener = urlOpener;
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
     * Muestra toda la biblioteca, sin filtros («01 Biblioteca»), recargándola desde la BD: puede
     * haber VNs nuevas añadidas desde la búsqueda.
     */
    public void showAll() {
        setQuery(LibraryQuery.ALL);
        load(true);
    }

    /**
     * Solo las VNs en este estado (al pulsarlo en la barra lateral). Quita la estantería, si había
     * (son excluyentes); la etiqueta se queda: se combinan.
     */
    public void showStatus(PlayStatus status) {
        setQuery(query.withStatus(status));
        load(true);
    }

    /** Solo las VNs de esta estantería. Quita el estado, si había; la etiqueta se queda. */
    public void showShelf(Shelf shelf) {
        setQuery(query.withShelf(shelf));
        load(true);
    }

    /** Muestra solo las VNs con esta etiqueta (al pulsarla en una ficha: sin más filtros). */
    public void showTag(VnTag tag) {
        setQuery(LibraryQuery.ALL.withTag(tag));
        load(true);
    }

    /** Los filtros de ahora, para que la barra lateral resalte el estado o la estantería. */
    public LibraryQuery query() {
        return query;
    }

    /** Se llama cada vez que cambian los filtros (también al quitar uno con su ✕). */
    public void setOnQueryChanged(Consumer<LibraryQuery> listener) {
        this.onQueryChanged = listener;
    }

    private void setQuery(LibraryQuery newQuery) {
        query = newQuery;
        onQueryChanged.accept(newQuery);
    }

    /** Se ha creado, cambiado o borrado una estantería: puede ser la que se está filtrando. */
    @Override
    public void shelvesChanged() {
        load(false);
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
        LibraryQuery filter = query; // la lambda corre en otro hilo: mejor no leer el campo desde allí
        BackgroundTasks.run(ctx.background(), () -> {
                    List<LibraryItem> items = library.findAllWithStatus();
                    Optional<CurrentRoute> current = Optional.empty();
                    List<LibraryEntry> recent = LibraryService.recentlyPlayed(items, 1);
                    if (!recent.isEmpty()) {
                        current = library.currentRoute(recent.getFirst().vn().id());
                    }
                    Optional<Shelf> shelf = Optional.empty();
                    Set<String> inShelf = Set.of();
                    if (filter.shelf() != null) {
                        // La estantería como está ahora (puede haber cambiado de nombre o no existir ya)
                        long id = filter.shelf().id();
                        shelf = ctx.shelfService().findAll().stream().filter(s -> s.id() == id).findFirst();
                        inShelf = ctx.shelfService().vnIdsIn(id);
                    }
                    Set<String> withTag = Set.of();
                    int withoutTags = 0;
                    if (filter.tag() != null) {
                        withTag = ctx.vnDetailsService().vnIdsWithTag(filter.tag().id());
                        withoutTags = ctx.vnDetailsService().countLibraryWithoutTags();
                    }
                    return new LibraryData(items, current, shelf, inShelf, withTag, withoutTags);
                },
                loaded -> {
                    data = loaded;
                    // La estantería filtrada se ha borrado: fuera ese filtro. Si sigue, con su nombre de ahora.
                    if (query.shelf() != null && loaded.shelf().isEmpty()) {
                        setQuery(query.withShelf(null));
                    } else if (loaded.shelf().isPresent() && query.shelf() != null
                            && loaded.shelf().get().id() == query.shelf().id()) {
                        query = query.withShelf(loaded.shelf().get());
                    }
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
        countLabel.setText(MessageFormat.format(COUNT, all.size()));
        renderEmptyLibrary(all.isEmpty());
        if (all.isEmpty()) {
            return;
        }
        renderContinue();
        renderActiveFilters();

        List<LibraryItem> visible = query.apply(all, data.inShelf(), data.withTag()).stream()
                .sorted(sortBox.getValue().comparator())
                .toList();
        covers.clear();
        grid.getChildren().clear();
        for (LibraryItem item : visible) {
            grid.getChildren().add(createTile(item));
        }

        boolean empty = visible.isEmpty();
        emptyLabel.setText(emptyMessage());
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);
    }

    /** Por qué no sale ninguna novela, según los filtros que haya. */
    private String emptyMessage() {
        VnTag tag = query.tag();
        if (query.shelf() != null) {
            return tag == null ? String.format(EMPTY_SHELF, query.shelf().name())
                    : String.format(NONE_IN_SHELF_WITH_TAG, query.shelf().name(), tag.name());
        }
        PlayStatus status = query.status();
        if (tag == null) {
            return String.format(NONE_WITH_STATUS, status == null ? "" : status.label());
        }
        return status == null ? String.format(NONE_WITH_TAG, tag.name())
                : String.format(NONE_WITH_BOTH, status.label(), tag.name());
    }

    /**
     * Los filtros activos, cada uno en un chip que lo quita al pulsarlo: «Estado: Jugando ✕» y
     * «Etiqueta: Drama ✕». Si se filtra por etiqueta y hay VNs cuya ficha nunca se ha abierto (sus
     * etiquetas aún no se han descargado), un aviso debajo.
     */
    private void renderActiveFilters() {
        activeFiltersBox.getChildren().clear();
        // No hace falta volver a la base de datos al quitar uno: sin él, sus datos no se usan
        if (query.status() != null) {
            activeFiltersBox.getChildren().add(filterChip(
                    String.format(STATUS_FILTER, query.status().label()), () -> {
                        setQuery(query.withStatus(null));
                        render();
                    }));
        }
        if (query.shelf() != null) {
            activeFiltersBox.getChildren().add(filterChip(
                    String.format(SHELF_FILTER, query.shelf().name()), () -> {
                        setQuery(query.withShelf(null));
                        render();
                    }));
        }
        if (query.tag() != null) {
            activeFiltersBox.getChildren().add(filterChip(
                    String.format(TAG_FILTER, query.tag().name()), () -> {
                        setQuery(query.withTag(null));
                        render();
                    }));
        }
        boolean note = query.tag() != null && data.withoutTags() > 0;
        filterNote.setText(note ? MessageFormat.format(WITHOUT_TAGS, data.withoutTags()) : "");
        filterNote.setVisible(note);
        filterNote.setManaged(note);
    }

    private static Button filterChip(String text, Runnable remove) {
        Button chip = new Button(text);
        chip.getStyleClass().addAll("chip", "active", "tag-filter");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setTooltip(new Tooltip(REMOVE_FILTER));
        chip.setOnAction(event -> remove.run());
        return chip;
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
            filterNote.setVisible(false);
            filterNote.setManaged(false);
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
        // Si el ejecutable ya no está, no se puede jugar: la ficha explica qué pasa y deja buscarlo
        boolean missing = data.items().stream()
                .anyMatch(i -> i.entry().vn().id().equals(vn.id()) && i.exeMissing());
        play.setDisable(running || missing);
        if (missing) {
            Tooltip.install(continueCard, new Tooltip(MISSING_TOOLTIP));
        }
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

    private VBox createTile(LibraryItem item) {
        LibraryEntry entry = item.entry();
        VisualNovel vn = entry.vn();
        // Portada de VNDB aún sin descargar (y ninguna elegida): hueco vacío en vez de iniciales
        CoverView cover = ctx.libraryService().coverPending(vn) && !ctx.coverService().hasOverride(entry)
                ? new CoverView((String) null, false, COVER_WIDTH, COVER_HEIGHT, null)
                : new CoverView(Covers.file(ctx, entry), Covers.sensitive(ctx, entry), COVER_WIDTH, COVER_HEIGHT,
                vn.title());
        cover.setRevealed(showSensitiveCheck.isSelected());
        if (item.exeMissing()) {
            cover.markMissing(); // el juego se ha movido o desinstalado
        }
        covers.add(cover);

        Label title = new Label(vn.title());
        title.getStyleClass().add("tile-title");
        title.setMaxWidth(COVER_WIDTH);
        Label developer = new Label(vn.developerNames());
        developer.getStyleClass().addAll("meta", "small");
        developer.setMaxWidth(COVER_WIDTH);

        // Estado (Jugando, En pausa...) y "Rutas 1/3 · 36 h" o "Sin jugar"
        Label statusPill = new Label(item.status().label());
        statusPill.getStyleClass().addAll("pill", StatusVisuals.pillClass(item.status()));
        statusPill.setMinWidth(Region.USE_PREF_SIZE); // la pastilla nunca se corta; el texto de al lado sí
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
        FlowPane statusRow = new FlowPane(8, 4, statusPill, detailLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPrefWrapLength(COVER_WIDTH);

        VBox tile = new VBox(4, cover, title, developer, statusRow);
        VBox.setMargin(title, new Insets(8, 0, 0, 0));
        tile.getStyleClass().add("tile");
        // Clic (con el botón principal) = abrir la ficha; clic derecho = el menú de la portada
        tile.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                navigator.showVnDetail(vn.id());
            }
        });
        tile.setOnContextMenuRequested(event -> {
            new LibraryTileMenu(ctx, navigator, urlOpener, item, () -> {
                load(false);
                navigator.refreshSidebar(); // contadores de estado y de estanterías
            }).show(tile, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return tile;
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

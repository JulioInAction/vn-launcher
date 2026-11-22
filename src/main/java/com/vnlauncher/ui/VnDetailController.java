package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.BlurLevel;
import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteProgress;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.model.VnDetails;
import com.vnlauncher.model.VnTag;
import com.vnlauncher.service.GameFiles;
import com.vnlauncher.util.PlayTime;
import com.vnlauncher.util.RelativeTime;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Ficha de una VN de la biblioteca (ficha-simplificada-*.png): cabecera con la portada, el título,
 * los chips de estado y estanterías, «Jugar» con «Opciones del juego» y «⋯», los datos en una línea,
 * las etiquetas, la descripción y la galería; debajo, las rutas y los personajes principales.
 *
 * <p>Recibe el AppContext entero en vez de cada servicio por separado porque usa casi todos;
 * con ocho parámetros en el constructor se volvía ilegible.
 */
public class VnDetailController implements PlaytimeAware, ShelvesAware {

    // --- Columnas de la cabecera (ficha-capturas-derecha-*.png)
    private static final double COVER_COLUMN = 220;
    private static final double COLUMN_GAP = 28;
    private static final double INFO_MIN = 400;
    private static final double GALLERY_MIN = 520;
    private static final double GALLERY_MAX = ScreenshotGallery.MAX_WIDTH;
    /** Ancho de página a partir del cual caben las tres columnas: 220 + 28 + 400 + 28 + 520. */
    static final double THREE_COLUMNS_MIN = COVER_COLUMN + COLUMN_GAP + INFO_MIN + COLUMN_GAP + GALLERY_MIN;

    private static final String CHARACTERS_ERROR = "No se pudieron cargar los personajes: %s";

    /** Descripción recortada a 3 líneas hasta pulsar «Leer más». */
    private static final int DESCRIPTION_LINES = 3;

    // --- Textos de la fila de datos: plantillas completas; lo que va entre <b> sale en negrita
    private static final String PLAYED = "<b>%s</b> jugados";
    private static final String LAST_PLAYED = "última partida <b>%s</b>";
    private static final String PLAYING_NOW = "<b>jugando ahora</b>";
    private static final String NEVER_PLAYED = "sin partidas todavía";
    private static final String ROUTES = "<b>%d de %d</b> rutas";
    private static final String NO_ROUTES = "sin rutas";

    // --- Botones y menú «⋯»
    private static final String OPTIONS_TOOLTIP = "Opciones del juego";
    private static final String MORE_TOOLTIP = "Más opciones";
    private static final String OPEN_CONFIG = "Abrir programa de configuración";
    private static final String OPEN_VNDB = "Ver en VNDB";
    private static final String REFRESH = "Actualizar desde VNDB";
    private static final String REMOVE = "Quitar de la biblioteca";

    // --- Lo que pasa al actualizar desde VNDB (a la derecha del «⋯»)
    private static final String REFRESHING = "Actualizando…";
    /** «Esperando al límite de VNDB (1:42)…» */
    private static final String WAITING = "Esperando al límite de VNDB (%d:%02d)…";
    private static final String REFRESHED = "✓  Actualizado";

    // --- Ejecutable perdido (fase 7)
    private static final String PLAY = "▶  Jugar";
    private static final String CHOOSE_EXE = "Elegir ejecutable";
    private static final String EXE_MISSING_TITLE = "No se encuentra el ejecutable";
    private static final String FIND_EXE = "Buscar el .exe";
    /** Un círculo con «!» dentro, de 22 px. */
    private static final String MISSING_ICON = "M11 1.5A9.5 9.5 0 1 0 11.01 1.5Z M11 6.5V12 M11 15.3V15.5";

    private final AppContext ctx;
    private final Navigator navigator;
    private final Consumer<String> urlOpener;
    private final String vnId;

    // --- cabecera
    @FXML private ScrollPane scroll;
    @FXML private VBox pageContent;
    @FXML private HBox headerRow;
    @FXML private VBox noticeBox;
    @FXML private StackPane coverBox;
    @FXML private Label titleLabel;
    @FXML private Label altTitleLabel;
    @FXML private Label metaLabel;
    @FXML private FlowPane chipsRow;
    @FXML private VBox exeNoticeBox;
    @FXML private Button playButton;
    @FXML private Button optionsButton;
    @FXML private Button moreButton;
    @FXML private ProgressIndicator refreshSpinner;
    @FXML private Label refreshStatus;
    @FXML private FlowPane statsRow;
    @FXML private VBox tagsContainer;
    @FXML private Label descriptionLabel;
    @FXML private Hyperlink readMoreLink;
    // --- rutas y personajes
    @FXML private VBox routesContainer;
    @FXML private CheckBox showSpoilersCheck;
    @FXML private CheckBox showSensitiveCheck;
    @FXML private Label charactersStatus;
    @FXML private FlowPane charactersPane;

    private RoutesPane routesPane;
    private LaunchFlow launchFlow;
    private TagChips tagChips;
    /** Galería de capturas; null si la VN no tiene (o aún no se han cargado). */
    private ScreenshotGallery gallery;
    /** Dónde está la galería: true = tercera columna, false = debajo, null = aún sin colocar. */
    private Boolean galleryWide;
    /** Ancho útil de la página (sin márgenes). Ver {@link #initialize()}. */
    private final DoubleProperty pageWidth = new SimpleDoubleProperty();

    private LibraryEntry entry;
    /** Si siguen ahí el ejecutable y el programa de configuración (se mira en segundo plano). */
    private GameFiles files = new GameFiles(GameFiles.FileStatus.NOT_SET, GameFiles.FileStatus.NOT_SET);
    /** Segundos jugados (suma de sesiones), para la fila de datos. */
    private long playSeconds;
    /** «Opciones del juego» mientras está abierto (para pintarle los cambios); si no, null. */
    private GameOptionsDialog optionsDialog;
    /** true mientras se actualiza desde VNDB (el «⋯» no deja pedirlo otra vez). */
    private boolean refreshing;
    /** Cuenta atrás de «Esperando al límite de VNDB (1:42)…». */
    private Timeline refreshCountdown;
    /** Borra «✓ Actualizado» a los 3 segundos. */
    private final PauseTransition refreshDoneHide = new PauseTransition(javafx.util.Duration.seconds(3));
    private StatusPicker statusPicker;
    private ShelfChips shelfChips;
    /** Todas las estanterías (para el menú y para comprobar el nombre de una nueva). */
    private List<Shelf> allShelves = List.of();
    private boolean descriptionExpanded = false;
    /** La descripción entera, con sus párrafos (la etiqueta enseña la recortada o esta). */
    private String descriptionText = "";
    /** true mientras se está lanzando (entre pulsar Jugar y que el juego arranque). */
    private boolean launching = false;
    /** Personajes cargados (todos, incluidos los spoiler); null mientras se cargan. */
    private List<VnCharacter> characters;
    /** Rutas actuales de la VN. */
    private List<Route> routes = List.of();
    /** Etiquetas y capturas; null mientras se cargan. */
    private VnDetails details;
    /** Personajes spoiler que el usuario ha destapado uno a uno con "Mostrar". */
    private final Set<String> revealedSpoilers = new HashSet<>();

    public VnDetailController(AppContext ctx, Navigator navigator, Consumer<String> urlOpener, String vnId) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.urlOpener = urlOpener;
        this.vnId = vnId;
    }

    @FXML
    private void initialize() {
        launchFlow = new LaunchFlow(ctx, navigator, playButton);
        // Fila de chips: primero el estado y detrás las estanterías con su «+»
        statusPicker = new StatusPicker(this::onStatusChosen);
        statusPicker.setDisable(true); // hasta que se cargue la VN
        chipsRow.getChildren().add(statusPicker);
        shelfChips = new ShelfChips(chipsRow, this::addToShelf, this::removeFromShelf, this::createShelfAndAdd);
        loadShelves();
        // Botones cuadrados con icono: engranaje (Opciones del juego) y «⋯» (Más opciones)
        optionsButton.setGraphic(Icons.gear());
        optionsButton.setTooltip(new Tooltip(OPTIONS_TOOLTIP));
        moreButton.setGraphic(Icons.moreDots());
        moreButton.setTooltip(new Tooltip(MORE_TOOLTIP));
        for (Button button : List.of(playButton, optionsButton, moreButton)) {
            button.setDisable(true); // hasta que se cargue la VN
        }
        refreshSpinner.setVisible(false);
        refreshDoneHide.setOnFinished(event -> clearRefreshStatus());
        loadHeader();
        reloadPlayData(); // tiempo jugado (suma de sesiones)

        // La zona de avisos, solo cuando tiene alguno
        noticeBox.managedProperty().bind(Bindings.isNotEmpty(noticeBox.getChildren()));
        // La descripción se recorta a 3 líneas: depende del ancho, así que se recalcula al cambiar
        descriptionLabel.widthProperty().addListener((obs, old, width) -> fitDescription());

        // Rutas: un componente propio (RoutesPane) que avisa cuando cambian las rutas.
        routesPane = new RoutesPane(ctx.routeService(), ctx.background(), ctx.imageCache(),
                ctx.contentFilter(), vnId);
        routesPane.setOnRoutesChanged(changed -> {
            routes = changed;
            renderStats();
            renderStatus();             // el estado automático depende de las rutas
            navigator.refreshSidebar(); // y los contadores de la barra, del estado
            renderCharacters(); // actualizar "Tiene ruta" / "Marcar con ruta"
        });
        routesContainer.getChildren().add(routesPane);
        routesPane.reload();

        // Ancho de la página para colocar la galería. Se mide el "viewport" del ScrollPane (la
        // parte visible), que depende solo de la ventana: el ancho del contenido no sirve, porque
        // con la galería a la derecha el contenido no puede encoger por debajo de las tres columnas.
        scroll.viewportBoundsProperty().addListener((obs, old, bounds) -> pageWidth.set(
                bounds.getWidth() - pageContent.getPadding().getLeft() - pageContent.getPadding().getRight()));
        pageWidth.addListener((obs, old, width) -> placeGallery());

        // Etiquetas: pulsar una abre la biblioteca filtrada por ella
        tagChips = new TagChips(navigator::showLibraryWithTag);
        tagsContainer.getChildren().add(tagChips);
        show(tagsContainer, false); // hasta que se carguen (sin ellas no deja hueco)
        loadDetails();

        // Personajes: se cargan a la vez, en otra tarea (la primera vez tardan: VNDB + imágenes).
        // «Mostrar spoilers» vale también para las etiquetas.
        showSpoilersCheck.selectedProperty().addListener((obs, oldValue, selected) -> {
            renderCharacters();
            renderTags();
        });
        // Valor inicial de "Mostrar spoilers": el de Ajustes → Contenido
        BackgroundTasks.run(ctx.background(), () -> ctx.settingsService().showSpoilers(),
                showSpoilersCheck::setSelected, error -> { });
        showSensitiveCheck.selectedProperty().addListener((obs, oldValue, selected) -> renderCharacters());
        loadCharacters();
    }

    /** Personajes: la primera vez tardan (VNDB + imágenes); después salen de la base de datos. */
    private void loadCharacters() {
        charactersStatus.setText("Cargando personajes...");
        charactersStatus.setManaged(true);
        BackgroundTasks.run(ctx.background(), () -> ctx.characterService().getPrimaryCharacters(vnId),
                loaded -> {
                    characters = loaded;
                    renderCharacters();
                },
                error -> {
                    // Sin conexión la primera vez: aviso en su sitio. Otro error no debería pasar.
                    if (ErrorReporter.isExpected(error)) {
                        charactersStatus.setText(String.format(CHARACTERS_ERROR, error.getMessage()));
                    } else {
                        charactersStatus.setText("");
                        ErrorReporter.report("No se han podido cargar los personajes.", error);
                    }
                });
    }

    /**
     * Etiquetas y capturas: la primera vez se piden a VNDB (1 petición, como los personajes);
     * después salen de la base de datos, también sin conexión.
     */
    private void loadDetails() {
        BackgroundTasks.run(ctx.background(), () -> ctx.vnDetailsService().get(vnId),
                loaded -> {
                    details = loaded;
                    renderTags();
                    renderGallery();
                },
                error -> {
                    if (!ErrorReporter.isExpected(error)) {
                        ErrorReporter.report("No se han podido cargar las etiquetas ni las capturas.", error);
                        return;
                    }
                    // Sin conexión la primera vez: se vuelven a pedir al abrir la ficha otra vez
                    Label message = new Label("No se han podido cargar las etiquetas ni las capturas. Se "
                            + "intentará de nuevo la próxima vez que abras la ficha.");
                    message.getStyleClass().add("muted");
                    message.setWrapText(true);
                    tagsContainer.getChildren().setAll(message);
                    show(tagsContainer, true);
                });
    }

    private void renderTags() {
        if (details == null) {
            return;
        }
        boolean showSexual = ctx.contentFilter().level() == BlurLevel.NONE;
        List<VnTag> tags = details.tagsToShow(showSpoilersCheck.isSelected(), showSexual);
        tagChips.setTags(tags);
        tagsContainer.getChildren().setAll(tagChips); // por si antes había un aviso de error
        show(tagsContainer, !tags.isEmpty());       // sin etiquetas, la sección no se ve
    }

    /** Crea la galería con las capturas (o la quita si no hay ninguna) y la coloca. */
    private void renderGallery() {
        if (gallery != null) {
            ((Pane) gallery.getParent()).getChildren().remove(gallery);
            gallery = null;
        }
        if (details.screenshots().isEmpty()) {
            return; // sin capturas no hay galería: la información ocupa ese sitio
        }
        ScreenshotSet shots = new ScreenshotSet(details.screenshots(), ctx.screenshotService(),
                ctx.background(), ctx.contentFilter());
        ScreenshotGallery created = new ScreenshotGallery(shots);
        created.setMaxHeight(Region.USE_PREF_SIZE); // en la fila de la cabecera, que no la estire
        // Clic en la captura principal: visor a pantalla completa. Al cerrarlo, la galería se queda
        // en la última captura que se vio.
        created.setOnOpen(index -> ScreenshotViewer.show(scroll, titleLabel.getText(), shots, index,
                created::select));
        gallery = created;
        galleryWide = null; // colocarla aunque no cambie el ancho
        placeGallery();
    }

    /**
     * Pone la galería como tercera columna de la cabecera (si caben las tres) o debajo de ella,
     * alineada a la izquierda y con 680 px como mucho. Se llama con cada cambio de ancho, pero solo
     * mueve la galería cuando se cruza el umbral (THREE_COLUMNS_MIN), no en cada píxel.
     */
    private void placeGallery() {
        if (gallery == null || pageWidth.get() <= 0) {
            return;
        }
        boolean wide = pageWidth.get() >= THREE_COLUMNS_MIN;
        if (galleryWide != null && galleryWide == wide) {
            return;
        }
        galleryWide = wide;
        if (gallery.getParent() != null) {
            ((Pane) gallery.getParent()).getChildren().remove(gallery);
        }
        if (wide) {
            // Ancho: lo que sobre tras la portada y 400 px de información, entre 520 y 680. Va
            // enlazado (binding) al ancho de la página: se recalcula solo, sin mover nada.
            DoubleBinding width = Bindings.createDoubleBinding(() -> Math.clamp(
                    pageWidth.get() - COVER_COLUMN - 2 * COLUMN_GAP - INFO_MIN, GALLERY_MIN, GALLERY_MAX), pageWidth);
            // min = pref = max: así la HBox, cuando le falta sitio, solo encoge la información
            gallery.minWidthProperty().bind(width);
            gallery.prefWidthProperty().bind(width);
            gallery.maxWidthProperty().bind(width);
            headerRow.getChildren().add(gallery);
        } else {
            gallery.minWidthProperty().unbind();
            gallery.prefWidthProperty().unbind();
            gallery.maxWidthProperty().unbind();
            gallery.setMinWidth(0);
            gallery.setPrefWidth(GALLERY_MAX);
            gallery.setMaxWidth(GALLERY_MAX); // la VBox la estira hasta aquí, a la izquierda
            pageContent.getChildren().add(pageContent.getChildren().indexOf(headerRow) + 1, gallery);
        }
    }

    // =========================================================== cabecera

    /** La VN leída de la base de datos y si siguen ahí sus archivos (mirado en el mismo hilo de fondo). */
    private record Loaded(LibraryEntry entry, GameFiles files) {
    }

    /** Lee la VN y pinta la cabecera. */
    private void loadHeader() {
        BackgroundTasks.run(ctx.background(),
                () -> ctx.libraryService().find(vnId).map(e -> new Loaded(e, GameFiles.of(e))),
                found -> {
                    if (found.isEmpty()) {
                        navigator.showLibrary(); // ya no está en la biblioteca
                        return;
                    }
                    renderVn(found.get().entry());
                    renderLaunchInfo(found.get());
                    optionsButton.setDisable(false);
                    moreButton.setDisable(false);
                },
                "No se ha podido cargar la ficha.");
    }

    /**
     * Portada (la elegida en la galería o la de VNDB) con su control «Cambiar portada» debajo, y
     * los datos de la VN que vienen de VNDB: títulos, desarrollador y descripción.
     */
    private void renderVn(LibraryEntry loaded) {
        VisualNovel vn = loaded.vn();
        // Si es sensible, clic en la portada para mostrarla u ocultarla
        CoverView cover = new CoverView(Covers.file(ctx, loaded), Covers.sensitive(ctx, loaded), 220, 330, vn.title())
                .revealOnClick();
        // Debajo, a 6 px, el control para cambiarla: siempre ocupa sus 44 px (50 en total con la
        // separación), así que al abrirse no mueve nada
        CoverChanger changer = new CoverChanger(220, () -> CoverGallery.show(playButton, ctx, loaded, () -> {
            loadHeader();               // pinta la portada nueva
            navigator.refreshSidebar(); // y en «Recientes»
        }));
        coverBox.getChildren().setAll(new VBox(CoverChanger.GAP, cover, changer));

        titleLabel.setText(vn.title());
        routesPane.setVnTitle(vn.title());
        altTitleLabel.setText(vn.altTitle() == null ? "" : vn.altTitle());
        altTitleLabel.setManaged(vn.altTitle() != null);
        metaLabel.setText(SearchController.meta(vn));
        renderDescription(vn.description());
    }

    /**
     * Parte de la cabecera que cambia al configurar o lanzar el juego. La ruta del ejecutable ya
     * no se ve en la ficha: va en el tooltip de «Jugar» y en «Opciones del juego».
     */
    private void renderLaunchInfo(Loaded loaded) {
        entry = loaded.entry();
        files = loaded.files();
        playButton.setTooltip(entry.hasExecutable() ? new Tooltip(entry.exePath()) : null);
        renderPlayButton();
        renderExeNotice();
        renderStatus();
        renderStats();
        if (optionsDialog != null) {
            optionsDialog.update(entry, files); // si está abierto, que enseñe lo recién guardado
        }
    }

    /**
     * Si el ejecutable ya no está (juego movido o desinstalado): aviso en rojo entre los chips y
     * las acciones, con la ruta y «Buscar el .exe». Si lo que falta es el programa de
     * configuración, no se avisa aquí: solo se marca en «Opciones del juego».
     */
    private void renderExeNotice() {
        exeNoticeBox.getChildren().clear();
        show(exeNoticeBox, files.exeMissing());
        if (!files.exeMissing()) {
            return;
        }
        SVGPath icon = new SVGPath();
        icon.setContent(MISSING_ICON);
        icon.getStyleClass().add("exe-notice-icon");
        Label title = new Label(EXE_MISSING_TITLE);
        title.getStyleClass().add("exe-notice-title");
        Label path = new Label(entry.exePath());
        path.getStyleClass().addAll("mono", "exe-notice-path");
        path.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        path.setTooltip(new Tooltip(entry.exePath()));
        path.setMinWidth(0);
        VBox texts = new VBox(2, title, path);
        texts.setMinWidth(0);
        HBox.setHgrow(texts, Priority.ALWAYS);
        Button find = new Button(FIND_EXE);
        find.setMinWidth(Region.USE_PREF_SIZE);
        find.setOnAction(event -> launchFlow.chooseExecutable(vnId, entry.vn().title(), entry.exePath(),
                this::reloadPlayData));
        HBox notice = new HBox(14, icon, texts, find);
        notice.setAlignment(Pos.CENTER_LEFT);
        notice.getStyleClass().add("exe-notice");
        exeNoticeBox.getChildren().add(notice);
    }

    /**
     * El selector «Estado»: el fijado a mano (si hay) y el que saldría solo con las rutas y las
     * partidas. Se vuelve a pintar al cambiar las rutas o al jugar.
     */
    private void renderStatus() {
        if (entry == null) {
            return;
        }
        PlayStatus automatic = PlayStatus.of(RouteSummary.of(routes), entry.lastPlayedAt());
        statusPicker.setValue(entry.statusOverride(), automatic);
        statusPicker.setDisable(false);
    }

    // =========================================================== Estanterías

    /** Las estanterías de esta VN y todas (en segundo plano). */
    private void loadShelves() {
        record Shelves(List<Shelf> mine, List<Shelf> all) {
        }
        BackgroundTasks.run(ctx.background(),
                () -> new Shelves(ctx.shelfService().shelvesOf(vnId), ctx.shelfService().findAll()),
                loaded -> {
                    allShelves = loaded.all();
                    shelfChips.setShelves(loaded.mine(), loaded.all());
                },
                "No se han podido leer las estanterías.");
    }

    /** Se ha cambiado alguna estantería desde la barra lateral (nombre, color, borrada...). */
    @Override
    public void shelvesChanged() {
        loadShelves();
    }

    private void addToShelf(Shelf shelf) {
        saveShelves(() -> ctx.shelfService().addVn(shelf.id(), vnId));
    }

    private void removeFromShelf(Shelf shelf) {
        saveShelves(() -> ctx.shelfService().removeVn(shelf.id(), vnId));
    }

    /** «+ Nueva estantería…» en el menú: se crea y la VN entra directamente en ella. */
    private void createShelfAndAdd() {
        ShelfDialog.create(chipsRow, allShelves).ifPresent(result -> saveShelves(() -> {
            Shelf created = ctx.shelfService().create(result.name(), result.color());
            ctx.shelfService().addVn(created.id(), vnId);
        }));
    }

    /** Guarda en segundo plano; después, chips de la ficha y contadores de la barra al día. */
    private void saveShelves(ShelfWork work) {
        BackgroundTasks.run(ctx.background(),
                () -> {
                    work.run();
                    return null;
                },
                done -> navigator.shelvesChanged(), // también vuelve a pintar estos chips
                "No se ha podido guardar la estantería.");
    }

    /** Trabajo con las estanterías que puede lanzar SQLException (un Runnable normal no puede). */
    @FunctionalInterface
    private interface ShelfWork {
        void run() throws java.sql.SQLException;
    }

    /** Elegido en el selector: un estado fijado a mano, o null para volver a «Automático». */
    private void onStatusChosen(PlayStatus status) {
        BackgroundTasks.run(ctx.background(),
                () -> {
                    ctx.libraryService().setStatus(vnId, status);
                    return status;
                },
                saved -> {
                    reloadPlayData();           // vuelve a leer la VN (con su estado) y lo pinta
                    navigator.refreshSidebar(); // los contadores de cada estado
                },
                "No se ha podido guardar el estado.");
    }

    /** Muestra u oculta un nodo sin que ocupe sitio cuando está oculto. */
    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** "▶ Jugar", "Lanzando..." o "Jugando..." según el estado. */
    /**
     * «▶ Jugar», «Lanzando...» o «Jugando...». Sin ejecutable elegido, el botón principal es
     * «Elegir ejecutable»; con uno que ya no está, «Jugar» se queda desactivado (el aviso de
     * encima explica por qué y deja buscarlo).
     */
    private void renderPlayButton() {
        boolean running = ctx.launchService().isRunning(vnId);
        boolean noExe = entry != null && !entry.hasExecutable();
        playButton.setText(running ? "Jugando..." : launching ? "Lanzando..." : noExe ? CHOOSE_EXE : PLAY);
        playButton.setDisable(entry == null || running || launching || files.exeMissing());
    }

    /** Recarga los datos de juego: última partida y tiempo total (en segundo plano). */
    private void reloadPlayData() {
        // Un record local: solo existe dentro de este método, para devolver dos valores juntos
        record PlayData(Optional<Loaded> loaded, long seconds) {
        }
        BackgroundTasks.run(ctx.background(),
                () -> new PlayData(ctx.libraryService().find(vnId).map(e -> new Loaded(e, GameFiles.of(e))),
                        ctx.libraryService().totalPlaySeconds(vnId)),
                data -> {
                    playSeconds = data.seconds();
                    data.loaded().ifPresent(this::renderLaunchInfo);
                },
                "No se ha podido leer el tiempo de juego.");
    }

    /**
     * La fila de datos: «37 min jugados · última partida hace 3 h · 0 de 4 rutas», con los valores
     * en negrita, los puntos en -vn-faint y la barra de las rutas al lado.
     */
    private void renderStats() {
        if (entry == null) {
            return;
        }
        RouteProgress progress = ctx.routeService().progressOf(routes);
        String lastPlayed = ctx.launchService().isRunning(vnId) ? PLAYING_NOW
                : entry.lastPlayedAt() == null ? NEVER_PLAYED
                : String.format(LAST_PLAYED, RelativeTime.format(entry.lastPlayedAt(), Instant.now(),
                        ZoneId.systemDefault()));
        String routeText = progress.total() == 0 ? NO_ROUTES
                : String.format(ROUTES, progress.completed(), progress.total());
        List<Node> parts = RichText.blocks(List.of(
                String.format(PLAYED, PlayTime.format(playSeconds)), lastPlayed, routeText));
        if (progress.total() > 0) {
            // La barra va pegada a «0 de 4 rutas»: si salta de línea, salta con ella
            ProgressBar bar = new ProgressBar(progress.fraction());
            bar.getStyleClass().addAll("thin-bar", "routes-mini-bar");
            bar.setMinWidth(80);
            bar.setPrefWidth(80);
            bar.setMaxWidth(80);
            HBox routesPart = new HBox(12, parts.getLast(), bar);
            routesPart.setAlignment(Pos.CENTER_LEFT);
            parts.set(parts.size() - 1, routesPart);
        }
        statsRow.getChildren().setAll(parts);
    }

    private void renderDescription(String description) {
        boolean empty = description == null || description.isBlank();
        descriptionText = empty ? "Sin descripción." : description;
        fitDescription();
    }

    /**
     * Recorta la descripción a {@link #DESCRIPTION_LINES} líneas (con «…» al final) y enseña «Leer
     * más» solo si de verdad no cabe. Cuántas líneas ocupa depende del ancho, así que se mide con un
     * Text igual que la etiqueta (misma letra, interlineado y ancho) cada vez que cambia el ancho.
     */
    private void fitDescription() {
        double width = descriptionLabel.getWidth();
        if (width <= 0) {
            descriptionLabel.setText(descriptionText);
            return; // aún sin colocar: se vuelve a llamar cuando tenga ancho
        }
        Text probe = new Text(descriptionText);
        probe.setFont(descriptionLabel.getFont());
        probe.setLineSpacing(descriptionLabel.getLineSpacing());
        probe.setWrappingWidth(width);
        double fullHeight = probe.getLayoutBounds().getHeight();
        probe.setText(String.join("\n", java.util.Collections.nCopies(DESCRIPTION_LINES, "Ag")));
        // + un interlineado: la etiqueta lo cuenta también detrás de la última línea al recortar
        double maxHeight = probe.getLayoutBounds().getHeight() + descriptionLabel.getLineSpacing();
        boolean overflows = fullHeight > maxHeight + 1;
        // Recortada, los párrafos van seguidos: una línea en blanco gastaría una de las tres
        descriptionLabel.setText(overflows && !descriptionExpanded
                ? descriptionText.replaceAll("\\s*\\n+\\s*", " ") : descriptionText);
        show(readMoreLink, overflows);
        readMoreLink.setText(descriptionExpanded ? "Leer menos" : "Leer más");
        // Con un alto máximo, la etiqueta corta el texto ella sola y pone «…» al final
        descriptionLabel.setMaxHeight(overflows && !descriptionExpanded ? Math.ceil(maxHeight) : Region.USE_PREF_SIZE);
    }

    @FXML
    private void onToggleDescription() {
        descriptionExpanded = !descriptionExpanded;
        fitDescription();
    }

    private void onOpenVndb() {
        urlOpener.accept("https://vndb.org/" + vnId);
    }

    /**
     * «⋯» (Más opciones): abrir el programa de configuración (si hay uno), ver en VNDB, actualizar
     * desde VNDB y, separado y en rojo, quitar de la biblioteca.
     */
    @FXML
    private void onMore() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("more-menu");
        if (entry != null && entry.hasConfigExecutable()) {
            menu.getItems().addAll(menuItem(OPEN_CONFIG, this::onOpenConfig), new SeparatorMenuItem());
        }
        MenuItem refresh = menuItem(REFRESH, this::onRefresh);
        refresh.setDisable(refreshing);
        MenuItem remove = menuItem(REMOVE, this::onRemove);
        remove.getStyleClass().add("danger-item");
        menu.getItems().addAll(menuItem(OPEN_VNDB, this::onOpenVndb), refresh, new SeparatorMenuItem(), remove);
        menu.show(moreButton, Side.BOTTOM, 0, 6);
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    /**
     * "Actualizar desde VNDB": vuelve a pedir la ficha, los personajes y las etiquetas y capturas,
     * y repinta la pantalla.
     * Las rutas, el tiempo de juego y el ejecutable no cambian (son datos del usuario).
     */
    private void onRefresh() {
        noticeBox.getChildren().clear();
        refreshing = true;
        showRefreshProgress();
        BackgroundTasks.run(ctx.background(), () -> ctx.refreshService().refresh(vnId),
                result -> {
                    refreshing = false;
                    if (!result.found()) {
                        clearRefreshStatus();
                        noticeBox.getChildren().setAll(new Notice(Notice.Kind.WARNING)
                                .title("VNDB ya no tiene esta novela")
                                .text("Puede que la hayan borrado o unido a otra. Se conservan los "
                                        + "datos que ya tenías."));
                        return;
                    }
                    showRefreshDone();
                    loadHeader();
                    loadCharacters();
                    loadDetails();
                    navigator.refreshSidebar(); // la portada o el título pueden haber cambiado
                },
                error -> {
                    refreshing = false;
                    clearRefreshStatus(); // los errores van en su aviso, no en esta línea
                    noticeBox.getChildren().setAll(Notice.vndbError(error, this::onRefresh));
                });
    }

    /**
     * A la derecha del «⋯», mientras se actualiza: un indicador de carga y «Actualizando…» o, si
     * hay que esperar por el límite de VNDB, «Esperando al límite de VNDB (1:42)…» con la cuenta
     * atrás (la tarea espera sola; esto solo lo cuenta).
     */
    private void showRefreshProgress() {
        refreshDoneHide.stop();
        refreshStatus.getStyleClass().remove("done");
        refreshSpinner.setVisible(true);
        stopCountdown();
        Runnable tick = () -> {
            Duration wait = ctx.rateLimiter().timeUntilAvailable();
            refreshStatus.setText(wait.isZero() ? REFRESHING
                    : String.format(WAITING, wait.toMinutes(), wait.toSecondsPart()));
            if (wait.isZero()) {
                stopCountdown();
            }
        };
        tick.run();
        if (!ctx.rateLimiter().timeUntilAvailable().isZero()) {
            refreshCountdown = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> tick.run()));
            refreshCountdown.setCycleCount(Timeline.INDEFINITE);
            refreshCountdown.play();
        }
    }

    /** «✓ Actualizado» en azul, que se va solo a los 3 segundos. */
    private void showRefreshDone() {
        stopCountdown();
        refreshSpinner.setVisible(false);
        refreshStatus.setText(REFRESHED);
        refreshStatus.getStyleClass().add("done");
        refreshDoneHide.playFromStart();
    }

    private void clearRefreshStatus() {
        stopCountdown();
        refreshSpinner.setVisible(false);
        refreshStatus.setText("");
        refreshStatus.getStyleClass().remove("done");
    }

    private void stopCountdown() {
        if (refreshCountdown != null) {
            refreshCountdown.stop();
            refreshCountdown = null;
        }
    }

    // =========================================================== lanzar el juego

    @FXML
    private void onPlay() {
        if (entry == null) {
            return;
        }
        if (!entry.hasExecutable()) {
            // «Elegir ejecutable»: el selector de archivos; al guardarlo, el botón pasa a «Jugar»
            launchFlow.chooseExecutable(vnId, entry.vn().title(), null, this::reloadPlayData);
            return;
        }
        launching = true;
        renderPlayButton();
        launchFlow.play(vnId, entry.vn().title(),
                () -> {
                    launching = false;
                    reloadPlayData();   // última partida + botón "Jugando..."
                    navigator.refreshSidebar(); // "Recientes" y estantería "Jugando"
                },
                () -> {
                    launching = false;
                    reloadPlayData();   // puede haber cambiado el ejecutable o LE en el intento
                });
    }

    /** Un juego se ha abierto o cerrado (aviso de MainController). */
    @Override
    public void playtimeChanged(String changedVnId) {
        if (vnId.equals(changedVnId)) {
            reloadPlayData();
        }
    }

    // =========================================================== Opciones del juego

    /** Engranaje: ejecutable, programa de configuración y Locale Emulator (GameOptionsDialog). */
    @FXML
    private void onOptions() {
        if (entry == null) {
            return;
        }
        optionsDialog = new GameOptionsDialog(optionsButton, entry, files, new GameOptionsDialog.Actions() {
            @Override
            public void chooseExecutable(Node owner) {
                // Con un LaunchFlow anclado al diálogo: el selector de archivos sale encima de él
                new LaunchFlow(ctx, navigator, owner).chooseExecutable(vnId, entry.vn().title(),
                        entry.exePath(), VnDetailController.this::reloadPlayData);
            }

            @Override
            public void chooseConfig(Node owner) {
                // Empieza en la carpeta del programa actual o, si no hay, en la del juego
                String startFrom = entry.hasConfigExecutable() ? entry.configExePath() : entry.exePath();
                new LaunchFlow(ctx, navigator, owner).chooseConfig(vnId, entry.vn().title(), startFrom,
                        VnDetailController.this::reloadPlayData);
            }

            @Override
            public void removeConfig() {
                onRemoveConfig();
            }

            @Override
            public void setLocaleEmulator(boolean use, Node owner) {
                onLocaleEmulatorToggled(use, owner);
            }
        });
        optionsDialog.showAndWait();
        optionsDialog = null;
    }

    /** «Abrir programa de configuración» en el «⋯». */
    private void onOpenConfig() {
        launchFlow.openConfig(vnId, entry.vn().title(), () -> { },
                this::reloadPlayData); // por si se eligió otro .exe en el diálogo de error
    }

    private void onRemoveConfig() {
        // Solo se olvida la ruta; el archivo no se toca. Por eso no hace falta confirmar.
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setConfigExecutable(vnId, null);
                    return null;
                },
                ignored -> reloadPlayData(),
                "No se ha podido quitar el programa de configuración.");
    }

    /** El interruptor de «Opciones del juego». {@code owner}: un nodo del diálogo, para el aviso. */
    private void onLocaleEmulatorToggled(boolean selected, Node owner) {
        if (entry == null || selected == entry.useLocaleEmulator()) {
            return; // nada que guardar
        }
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setUseLocaleEmulator(vnId, selected);
                    // Si se activa y no sabemos dónde está LEProc.exe, avisar ya
                    return selected && ctx.settingsService().localeEmulatorPath().isEmpty();
                },
                needsPath -> {
                    reloadPlayData();
                    if (needsPath) {
                        ModalDialog dialog = new ModalDialog(owner, 480)
                                .title("Falta indicar dónde está Locale Emulator", false)
                                .text("Elige el archivo LEProc.exe en Ajustes → Locale Emulator. "
                                        + "Solo hay que hacerlo una vez para todas las novelas.");
                        dialog.button("later", "Más tarde", ModalDialog.ButtonKind.SECONDARY);
                        dialog.button("settings", "Ir a Ajustes", ModalDialog.ButtonKind.PRIMARY);
                        if ("settings".equals(dialog.showAndWait())) {
                            if (optionsDialog != null) {
                                optionsDialog.close(); // primero se cierra «Opciones del juego»
                            }
                            navigator.showSettings();
                        }
                    }
                },
                "No se ha podido guardar la opción de Locale Emulator.");
    }

    // =========================================================== personajes

    /**
     * Dibuja las tarjetas de personaje. Los spoiler aparecen tapados ("Oculto por spoiler")
     * salvo que esté marcado "Mostrar spoilers" o se hayan destapado uno a uno.
     */
    private void renderCharacters() {
        if (characters == null) {
            return;
        }
        boolean showSpoilers = showSpoilersCheck.isSelected();
        boolean showSensitive = showSensitiveCheck.isSelected();
        // Ids de los personajes que ya tienen alguna ruta (un Set para buscar rápido)
        Set<String> withRoute = routes.stream()
                .map(Route::characterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        charactersPane.getChildren().clear();
        for (VnCharacter vc : characters) {
            GameCharacter c = vc.character();
            Path file = c.imagePath() == null ? null : ctx.imageCache().resolve(c.imagePath());
            boolean hidden = vc.isSpoiler() && !showSpoilers && !revealedSpoilers.contains(c.id());
            CharacterCard.Options options = new CharacterCard.Options(hidden,
                    ctx.contentFilter().isSensitive(c.imageSexual(), c.imageViolence()),
                    showSensitive, withRoute.contains(c.id()));
            charactersPane.getChildren().add(new CharacterCard(vc, file, options,
                    () -> routesPane.addRouteFor(vc),
                    () -> {
                        revealedSpoilers.add(c.id());
                        renderCharacters();
                    }));
        }
        // El diálogo de rutas solo ofrece personajes spoiler si se están mostrando
        routesPane.setCharacters(characters, showSpoilers);

        long spoilerCount = characters.stream().filter(VnCharacter::isSpoiler).count();
        showSpoilersCheck.setVisible(spoilerCount > 0);
        showSpoilersCheck.setManaged(spoilerCount > 0);
        boolean anySensitive = characters.stream().map(VnCharacter::character)
                .anyMatch(ch -> ctx.contentFilter().isSensitive(ch.imageSexual(), ch.imageViolence()));
        showSensitiveCheck.setVisible(anySensitive);
        showSensitiveCheck.setManaged(anySensitive);
        charactersStatus.setText("");
        charactersStatus.setManaged(false);
        if (characters.isEmpty()) {
            // Estado vacío (vacio-personajes.png): las rutas se pueden crear igualmente
            Button addRoute = new Button("+  Añadir ruta");
            addRoute.setOnAction(event -> routesPane.openCreateDialog());
            VBox empty = EmptyState.create(null, "VNDB no tiene personajes principales para esta novela",
                    addRoute,
                    "Puedes crear las rutas igualmente y escribir el nombre de la heroína a mano. Si "
                            + "alguien los añade en VNDB, aparecerán al pulsar «Actualizar desde VNDB».");
            empty.getStyleClass().add("empty-dashed");
            // Que ocupe todo el ancho de la sección
            empty.prefWidthProperty().bind(charactersPane.widthProperty());
            charactersPane.getChildren().setAll(empty);
        }
    }

    // =========================================================== otras acciones

    /** «‹ Biblioteca»: vuelve a la biblioteca como estaba (con su filtro de estado o de etiqueta). */
    @FXML
    private void onBack() {
        navigator.backToLibrary();
    }

    /** «Quitar de la biblioteca» en el «⋯». */
    private void onRemove() {
        LibraryRemoval.confirmAndRemove(ctx, moreButton, entry,
                () -> moreButton.setDisable(true),
                navigator::showLibrary,
                () -> moreButton.setDisable(false));
    }
}

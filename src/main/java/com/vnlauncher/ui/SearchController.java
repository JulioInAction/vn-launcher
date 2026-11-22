package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.BlurLevel;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.SearchHit;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnExtras;
import com.vnlauncher.model.VnStats;
import com.vnlauncher.util.VnFacts;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vista "Buscar": campo de búsqueda, resultados como tarjetas y un panel con los detalles del
 * resultado elegido.
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
 *
 * <p>Peticiones a VNDB: la búsqueda es UNA petición que ya trae los datos ligeros del panel
 * (duración, valoración, idiomas...). Las etiquetas, capturas y relaciones se piden aparte al
 * seleccionar un resultado (1 petición, que se guarda en una caché en memoria).
 */
public class SearchController {

    /** Ancho útil del panel: 540 menos 1 px de borde y 24 de margen a cada lado. */
    static final double INNER_WIDTH = 540 - 2 - 2 * 24;
    private static final double COVER_WIDTH = 180;
    private static final double COVER_HEIGHT = 270;
    private static final double HEADER_GAP = 20;
    private static final double COLUMN_GAP = 16;
    private static final double COLUMN_WIDTH = (INNER_WIDTH - COLUMN_GAP) / 2;
    private static final double SHOT_GAP = 8;
    /** Tres capturas en 16:9 que llenan el ancho: 158 × 89. */
    private static final double SHOT_WIDTH = Math.floor((INNER_WIDTH - 2 * SHOT_GAP) / 3);
    private static final double SHOT_HEIGHT = Math.round(SHOT_WIDTH * 9 / 16);
    /** Por debajo de este ancho, la tarjeta de resultado pone el botón bajo el título. */
    private static final double COMPACT_CARD_WIDTH = 480;
    private static final int MAX_TAGS = 5;
    private static final int DESCRIPTION_LINES = 6;
    /**
     * Espera antes de pedir los detalles de un resultado: si se pasa por varios seguidos, solo se
     * pide el del último en el que se para.
     */
    private static final javafx.util.Duration EXTRAS_DELAY = javafx.util.Duration.millis(250);

    /** Lo que devuelve la tarea de búsqueda: resultados + ids que ya están en la biblioteca. */
    private record SearchResult(List<SearchHit> hits, Set<String> inLibrary) {
    }

    private final AppContext ctx;
    private final Navigator navigator;

    @FXML private TextField queryField;
    @FXML private Button searchButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;
    @FXML private VBox resultsBox;
    @FXML private VBox resultsArea;
    @FXML private VBox noticeBox;
    @FXML private VBox emptyBox;
    @FXML private VBox hintCard;
    @FXML private ScrollPane previewScroll;
    @FXML private VBox previewContent;
    @FXML private VBox previewFooter;

    /** Búsqueda en curso (para poder cancelarla desde el aviso de límite). */
    private Task<SearchResult> searchTask;
    /** Cuenta atrás del aviso de límite. */
    private Timeline countdown;

    private List<SearchHit> hits = List.of();
    /** Ids de VN que están en la biblioteca (para "✓ En tu biblioteca"). */
    private final Set<String> inLibrary = new HashSet<>();
    /** Tarjeta de cada resultado, para marcar la seleccionada. */
    private final Map<String, Node> cards = new HashMap<>();
    private SearchHit selected;

    // --- Detalles que se piden al seleccionar (etiquetas, capturas y relación)
    private final PauseTransition extrasDelay = new PauseTransition(EXTRAS_DELAY);
    /** Ids cuyos detalles se están pidiendo ahora mismo (para no pedirlos dos veces a la vez). */
    private final Set<String> extrasLoading = new HashSet<>();
    /** Ids cuyos detalles fallaron (se muestra "Reintentar"). */
    private final Set<String> extrasFailed = new HashSet<>();
    /**
     * Partes del panel que se rellenan cuando llegan los detalles. Solo se cambian ellas, no el
     * panel entero: así no se pierde el scroll ni la descripción desplegada.
     */
    private Label relationValue;
    private VBox extrasBox;

    public SearchController(AppContext ctx, Navigator navigator) {
        this.ctx = ctx;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        setBusy(false);
        showEmpty(null);
        // Antes de buscar nada solo se ve la pista bajo la barra (sin resultados ni panel)
        resultsArea.setVisible(false);
        resultsArea.setManaged(false);
        renderPreview();
    }

    // =========================================================== buscar

    /** Se llama al pulsar Enter en la caja de texto o el botón Buscar (onAction en el FXML). */
    @FXML
    private void onSearch() {
        String text = queryField.getText();
        if (text == null || text.isBlank()) {
            return; // no gastamos una petición de VNDB en una búsqueda vacía
        }
        String query = text.strip();
        // La pista "Escribe un título y pulsa Enter" ya no hace falta
        hintCard.setVisible(false);
        hintCard.setManaged(false);
        setBusy(true);
        clearNotice();
        statusLabel.setText("Buscando \"" + query + "\"...");

        // ¿Hay que esperar por el límite de VNDB? Se pregunta ANTES, sin gastar petición. La tarea
        // esperará sola dentro del RateLimiter; mientras, se muestra la cuenta atrás.
        Duration wait = ctx.rateLimiter().timeUntilAvailable();

        searchTask = BackgroundTasks.run(ctx.background(),
                () -> new SearchResult(ctx.catalogService().search(query), libraryIds()), // en segundo plano
                result -> {                                                              // en la interfaz
                    clearNotice();
                    hits = result.hits();
                    inLibrary.clear();
                    inLibrary.addAll(result.inLibrary());
                    statusLabel.setText(hits.size() == 1 ? "1 resultado" : hits.size() + " resultados");
                    showEmpty(hits.isEmpty() ? query : null);
                    renderResults();
                    selected = null; // búsqueda nueva: el panel se dibuja de cero
                    select(hits.isEmpty() ? null : hits.getFirst());
                    setBusy(false);
                },
                error -> {
                    clearNotice();
                    statusLabel.setText("");
                    showError(error);
                    setBusy(false);
                });
        if (!wait.isZero()) {
            showRateLimitWait(wait);
        }
    }

    /**
     * Se sale de «Buscar» (MainController la descarta): se cancela la búsqueda si sigue en marcha,
     * la cuenta atrás del límite y la petición de detalles que estuviera esperando.
     */
    void close() {
        if (searchTask != null) {
            searchTask.cancel(); // interrumpe el hilo, también si espera en el RateLimiter
        }
        if (countdown != null) {
            countdown.stop();
        }
        extrasDelay.stop();
    }

    // =========================================================== avisos y estados vacíos

    /** Aviso azul "Has llegado al límite de VNDB" con cuenta atrás y "Cancelar búsqueda". */
    private void showRateLimitWait(Duration wait) {
        Instant end = Instant.now().plus(wait);
        Notice notice = new Notice(Notice.Kind.WAIT).title("Has llegado al límite de VNDB");
        notice.action("Cancelar búsqueda", () -> {
            searchTask.cancel(); // interrumpe el hilo que espera en el RateLimiter
            clearNotice();
            statusLabel.setText("Búsqueda cancelada.");
            setBusy(false);
        });
        Runnable tick = () -> {
            long left = Math.max(0, Duration.between(Instant.now(), end).toSeconds());
            notice.text("Son 200 peticiones cada 5 minutos. Tu búsqueda se hará sola en "
                    + left / 60 + ":" + String.format("%02d", left % 60) + ".");
            notice.progress(1.0 - (double) left / Math.max(1, wait.toSeconds()));
        };
        tick.run();
        countdown = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> tick.run()));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
        noticeBox.getChildren().setAll(notice);
    }

    /** Errores de red: aviso naranja dentro de la pantalla con "Reintentar". */
    private void showError(Throwable error) {
        noticeBox.getChildren().setAll(Notice.vndbError(error, this::onSearch));
        // Sin resultados anteriores que enseñar, el aviso va solo (sin la columna de resultados vacía)
        emptyBox.setVisible(false);
        emptyBox.setManaged(false);
        boolean hasResults = !hits.isEmpty();
        resultsArea.setVisible(hasResults);
        resultsArea.setManaged(hasResults);
    }

    private void clearNotice() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
        noticeBox.getChildren().clear();
    }

    /** Muestra "No hay resultados para «x»" (o lo oculta si query es null). */
    private void showEmpty(String query) {
        boolean empty = query != null;
        emptyBox.getChildren().clear();
        if (empty) {
            emptyBox.getChildren().add(EmptyState.create(
                    EmptyState.svgIcon(EmptyState.SEARCH_ICON, 1.3),
                    "No hay resultados para «" + query + "»", null,
                    "Revisa la ortografía o busca solo una parte del título.",
                    "Prueba con el título original en japonés o en romaji."));
            statusLabel.setText("");
        }
        emptyBox.setVisible(empty);
        emptyBox.setManaged(empty);
        resultsArea.setVisible(!empty);
        resultsArea.setManaged(!empty);
    }

    /** Ids de la biblioteca. Se llama en segundo plano, junto con la búsqueda. */
    private Set<String> libraryIds() throws SQLException {
        return ctx.libraryService().findAll().stream()
                .map(LibraryEntry::vn)
                .map(VisualNovel::id)
                .collect(Collectors.toSet());
    }

    /** Mientras se busca: ruedecita visible y controles desactivados (evita búsquedas dobles). */
    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        queryField.setDisable(busy);
        searchButton.setDisable(busy);
    }

    // =========================================================== añadir

    /** Añade la VN a la biblioteca: guarda sus datos (0 peticiones) y descarga la portada. */
    private void addToLibrary(VisualNovel vn, Button sourceButton) {
        sourceButton.setDisable(true);
        sourceButton.setText("Añadiendo...");
        BackgroundTasks.run(ctx.background(),
                () -> {
                    // Lo que ya se vio de ella (etiquetas, capturas) se guarda ahora: la ficha no lo pide otra vez
                    ctx.previewService().remember(vn, ctx.catalogService().cachedDetails(vn.id()).orElse(null));
                    ctx.libraryService().addToLibrary(vn);
                    return null;
                },
                ignored -> {
                    inLibrary.add(vn.id());
                    statusLabel.setText("\"" + vn.title() + "\" añadida a tu biblioteca");
                    renderResults();
                    renderFooter();
                    navigator.refreshSidebar(); // cambia el número de "Pendientes"
                },
                error -> {
                    statusLabel.setText("");
                    ErrorReporter.report("No se ha podido añadir la novela a la biblioteca.", error);
                    renderResults();
                    renderFooter();
                });
    }

    // =========================================================== lista de resultados

    private void renderResults() {
        cards.clear();
        resultsBox.getChildren().clear();
        for (SearchHit hit : hits) {
            Node card = resultCard(hit);
            cards.put(hit.vn().id(), card);
            resultsBox.getChildren().add(card);
        }
        markSelected();
    }

    /** Tarjeta de un resultado: miniatura · título y desarrollador/año · Añadir o ✓ En tu biblioteca. */
    private Node resultCard(SearchHit hit) {
        VisualNovel vn = hit.vn();
        CoverView thumb = new CoverView(vn.imageUrl(), isSensitive(vn), 56, 84, vn.title());

        Label title = new Label(vn.title());
        title.getStyleClass().add("result-title");
        // Títulos largos ("CLANNAD - Hikari Mimamoru Sakamichi de"): en varias líneas, sin cortarse
        title.setWrapText(true);
        Label meta = new Label(meta(vn));
        meta.getStyleClass().add("meta");
        VBox texts = new VBox(4, title, meta);
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0); // permite que los textos se corten con "..." si no caben

        // Solo se marca lo que ya está en la biblioteca; para añadir, el botón del panel de la derecha
        Node action = null;
        if (inLibrary.contains(vn.id())) {
            Label done = new Label("✓  En tu biblioteca");
            done.getStyleClass().add("in-library");
            done.setMinWidth(Region.USE_PREF_SIZE);
            action = done;
        }

        HBox card = new HBox(16, thumb, texts);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("result-card");
        card.setOnMouseClicked(event -> {
            select(hit);
            if (event.getClickCount() == 2) {
                openFicha(hit); // doble clic: la ficha (la vista previa, si aún no está en la biblioteca)
            }
        });
        // Con la ventana estrecha (la lista comparte fila con el panel de 540 px), el botón o
        // «✓ En tu biblioteca» pasa debajo del desarrollador: si no, al título le quedarían 70 px y
        // se partiría letra a letra. El ancho de la tarjeta no depende de dónde vaya el botón, así
        // que moverlo no vuelve a disparar el listener.
        if (action == null) {
            return card;
        }
        card.getChildren().add(action);
        Node moving = action;
        VBox.setMargin(moving, new Insets(6, 0, 0, 0));
        card.widthProperty().addListener((obs, old, width) -> {
            boolean compact = width.doubleValue() < COMPACT_CARD_WIDTH;
            if (compact && moving.getParent() == card) {
                card.getChildren().remove(moving);
                texts.getChildren().add(moving);
            } else if (!compact && moving.getParent() == texts) {
                texts.getChildren().remove(moving);
                card.getChildren().add(moving);
            }
        });
        return card;
    }

    /** «Ver ficha» / doble clic: la vista previa; si ya está en la biblioteca, su ficha de siempre. */
    private void openFicha(SearchHit hit) {
        if (inLibrary.contains(hit.vn().id())) {
            navigator.showVnDetailFromSearch(hit.vn().id());
        } else {
            navigator.showVnPreview(hit);
        }
    }

    /**
     * Se vuelve a «Buscar» desde una ficha: puede haberse añadido (o quitado) la VN, y los resultados
     * tienen que decirlo. Lo demás (resultados, selección, scroll, descripción desplegada) no se toca.
     */
    void libraryChanged() {
        BackgroundTasks.run(ctx.background(), this::libraryIds,
                ids -> {
                    if (ids.equals(inLibrary)) {
                        return;
                    }
                    inLibrary.clear();
                    inLibrary.addAll(ids);
                    renderResults();
                    renderFooter();
                },
                error -> { }); // sin esto solo se queda «Añadir» donde ya está añadida: no merece un aviso
    }

    private void markSelected() {
        cards.forEach((id, card) -> {
            card.getStyleClass().remove("selected");
            if (isSelected(id)) {
                card.getStyleClass().add("selected");
            }
        });
    }

    // =========================================================== selección y detalles

    /** Elige un resultado: lo marca, dibuja el panel y pide sus detalles si hace falta. */
    private void select(SearchHit hit) {
        if (hit != null && selected != null && hit.vn().id().equals(selected.vn().id())) {
            return; // ya se está viendo: no se redibuja (no se pierde el scroll del panel)
        }
        selected = hit;
        markSelected();
        if (hit != null) {
            extrasFailed.remove(hit.vn().id()); // si falló antes, al volver a elegirlo se reintenta
        }
        renderPreview();
        requestExtras();
    }

    /**
     * Pide los detalles del seleccionado tras una breve espera (EXTRAS_DELAY), salvo que ya estén en
     * la caché o pidiéndose. Si antes de que pase la espera se elige otro, se pide solo el otro.
     */
    private void requestExtras() {
        extrasDelay.stop();
        if (selected == null) {
            return;
        }
        String id = selected.vn().id();
        if (ctx.catalogService().cachedExtras(id).isPresent() || extrasLoading.contains(id)) {
            return;
        }
        extrasDelay.setOnFinished(event -> loadExtras(id));
        extrasDelay.playFromStart();
    }

    /** 1 petición a VNDB (pasa por el RateLimiter); el resultado queda en la caché del servicio. */
    private void loadExtras(String id) {
        if (!isSelected(id) || extrasLoading.contains(id)) {
            return;
        }
        extrasLoading.add(id);
        BackgroundTasks.run(ctx.background(),
                () -> ctx.catalogService().extras(id),
                extras -> {
                    extrasLoading.remove(id);
                    if (isSelected(id)) { // si ya se ha elegido otro, se queda en la caché para luego
                        renderExtras();
                    }
                },
                error -> {
                    extrasLoading.remove(id);
                    extrasFailed.add(id);
                    if (isSelected(id)) {
                        renderExtras();
                    }
                });
    }

    private boolean isSelected(String vnId) {
        return selected != null && selected.vn().id().equals(vnId);
    }

    // =========================================================== panel

    /**
     * Panel del resultado elegido (de arriba abajo): cabecera con portada, título y estado · seis
     * datos en dos columnas · etiquetas · capturas · descripción · y, fuera del scroll, el botón.
     */
    private void renderPreview() {
        previewContent.getChildren().clear();
        relationValue = null;
        extrasBox = null;
        previewScroll.setVvalue(0);
        if (selected == null) {
            Label hint = new Label("Elige un resultado para ver sus detalles.");
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);
            hint.setMaxWidth(INNER_WIDTH);
            previewContent.getChildren().add(hint);
            renderFooter();
            return;
        }
        VisualNovel vn = selected.vn();
        VnStats stats = selected.stats();

        ExpandableText description = new ExpandableText(vn.description(), DESCRIPTION_LINES);
        description.setMaxWidth(INNER_WIDTH);

        extrasBox = new VBox(24);
        extrasBox.setMaxWidth(INNER_WIDTH);
        previewContent.getChildren().addAll(header(vn, stats), facts(vn, stats), extrasBox, description);
        renderExtras();
        renderFooter();
    }

    /** Portada 180 × 270 y, a su derecha, título, título original, «Desarrollador · año» y estado. */
    private Node header(VisualNovel vn, VnStats stats) {
        CoverView cover = new CoverView(vn.imageUrl(), isSensitive(vn), COVER_WIDTH, COVER_HEIGHT, vn.title())
                .revealOnClick();

        Label title = new Label(vn.title());
        title.getStyleClass().add("preview-title");
        title.setWrapText(true);
        VBox texts = new VBox(8, title);
        if (vn.altTitle() != null && !vn.altTitle().isBlank() && !vn.altTitle().equals(vn.title())) {
            Label original = new Label(vn.altTitle());
            original.getStyleClass().addAll("meta", "original-text");
            original.setWrapText(true);
            texts.getChildren().add(original);
        }
        Label meta = new Label(meta(vn));
        meta.getStyleClass().add("preview-meta");
        meta.setWrapText(true);
        texts.getChildren().add(meta);

        String status = VnFacts.devStatus(stats.devStatus());
        if (status != null) {
            Label pill = new Label(status);
            pill.getStyleClass().addAll("pill", "pill-devstatus");
            VBox.setMargin(pill, new Insets(2, 0, 0, 0));
            texts.getChildren().add(pill);
        }
        texts.setMinWidth(0);
        HBox.setHgrow(texts, Priority.ALWAYS);

        HBox header = new HBox(HEADER_GAP, cover, texts);
        header.setMaxWidth(INNER_WIDTH);
        return header;
    }

    /** Seis datos en dos columnas: etiqueta pequeña y el valor debajo. */
    private Node facts(VisualNovel vn, VnStats stats) {
        VBox relation = fact("Relaciones", "");
        relationValue = (Label) relation.getChildren().get(1);
        String developers = vn.developerNames().isEmpty() ? "Desconocido" : vn.developerNames();

        VBox grid = new VBox(14,
                factRow(fact("Duración", VnFacts.length(stats.lengthMinutes(), stats.lengthVotes())),
                        fact("Valoración", VnFacts.rating(stats.rating(), stats.voteCount()))),
                factRow(fact("Idiomas", VnFacts.languages(stats.languages(), stats.originalLanguage())),
                        fact("Plataformas", VnFacts.platforms(stats.platforms()))),
                factRow(relation, fact("Desarrollador", developers)));
        grid.setMaxWidth(INNER_WIDTH);
        return grid;
    }

    private static HBox factRow(VBox left, VBox right) {
        return new HBox(COLUMN_GAP, left, right);
    }

    private static VBox fact(String name, String value) {
        Label label = new Label(name);
        label.getStyleClass().add("fact-label");
        Label text = new Label(value);
        text.getStyleClass().add("fact-value");
        text.setWrapText(true);
        VBox box = new VBox(4, label, text);
        // Ancho fijo: las dos columnas miden lo mismo aunque un valor sea más largo
        box.setMinWidth(COLUMN_WIDTH);
        box.setPrefWidth(COLUMN_WIDTH);
        box.setMaxWidth(COLUMN_WIDTH);
        return box;
    }

    /**
     * Rellena la relación, las etiquetas y las capturas: con los datos si ya están en la caché,
     * "Cargando..." mientras se piden, o un aviso con "Reintentar" si fallaron.
     */
    private void renderExtras() {
        if (selected == null || extrasBox == null) {
            return;
        }
        String id = selected.vn().id();
        Optional<VnExtras> cached = ctx.catalogService().cachedExtras(id);
        extrasBox.getChildren().clear();
        relationValue.getStyleClass().remove("muted");

        if (cached.isPresent()) {
            VnExtras extras = cached.get();
            relationValue.setText(extras.relation() == null ? "Ninguna" : extras.relation());
            extrasBox.getChildren().add(tagsSection(extras.tags()));
            if (!extras.screenshots().isEmpty()) {
                extrasBox.getChildren().add(screenshotsRow(extras.screenshots()));
            }
        } else if (extrasFailed.contains(id)) {
            relationValue.setText("No disponible");
            relationValue.getStyleClass().add("muted");
            Label message = new Label("No se han podido cargar las etiquetas ni las capturas.");
            message.getStyleClass().add("muted");
            message.setWrapText(true);
            Hyperlink retry = new Hyperlink("Reintentar");
            retry.setOnAction(event -> {
                extrasFailed.remove(id);
                renderExtras();
                loadExtras(id);
            });
            extrasBox.getChildren().add(new VBox(4, message, retry));
        } else {
            // Mientras llegan: los mismos huecos que ocuparán, para que el panel no dé saltos
            relationValue.setText("Cargando...");
            relationValue.getStyleClass().add("muted");
            Label loading = new Label("Cargando...");
            loading.getStyleClass().add("muted");
            HBox placeholders = new HBox(SHOT_GAP);
            for (int i = 0; i < 3; i++) {
                Region box = new Region();
                box.getStyleClass().add("cover");
                box.setMinSize(SHOT_WIDTH, SHOT_HEIGHT);
                box.setMaxSize(SHOT_WIDTH, SHOT_HEIGHT);
                placeholders.getChildren().add(box);
            }
            extrasBox.getChildren().addAll(new VBox(10, tagsHeading(), loading), placeholders);
        }
    }

    /**
     * «Etiquetas principales (sin spoilers)»: las 5 con mejor puntuación. Las de contenido sexual
     * solo se ven con «No difuminar» (VNDB también las oculta por defecto).
     */
    private Node tagsSection(List<VnExtras.Tag> tags) {
        boolean showSexual = ctx.contentFilter().level() == BlurLevel.NONE;
        List<VnExtras.Tag> shown = tags.stream()
                .filter(tag -> showSexual || !tag.isSexual())
                .limit(MAX_TAGS)
                .toList();
        if (shown.isEmpty()) {
            Label none = new Label("Sin etiquetas.");
            none.getStyleClass().add("muted");
            return new VBox(10, tagsHeading(), none);
        }
        // FlowPane: los chips se colocan en fila y saltan a la siguiente si no caben
        FlowPane chips = new FlowPane(8, 8);
        chips.setPrefWrapLength(INNER_WIDTH);
        for (VnExtras.Tag tag : shown) {
            Label chip = new Label(tag.name());
            chip.getStyleClass().add("tag-chip");
            chips.getChildren().add(chip);
        }
        return new VBox(10, tagsHeading(), chips);
    }

    private static Label tagsHeading() {
        Label heading = new Label("Etiquetas principales (sin spoilers)");
        heading.getStyleClass().add("fact-label");
        return heading;
    }

    /** Capturas en 16:9, enteras (sin recortar). Las sensibles, difuminadas hasta hacer clic. */
    private Node screenshotsRow(List<VnExtras.Screenshot> screenshots) {
        HBox row = new HBox(SHOT_GAP);
        for (VnExtras.Screenshot shot : screenshots) {
            boolean sensitive = ctx.contentFilter().isSensitive(shot.sexual(), shot.violence());
            row.getChildren().add(
                    new CoverView(shot.thumbnailUrl(), sensitive, SHOT_WIDTH, SHOT_HEIGHT, null).revealOnClick());
        }
        return row;
    }

    /**
     * Pie del panel, fuera del scroll: «Añadir a la biblioteca» y «Ver ficha» (la vista previa) o, si
     * ya está en la biblioteca, «✓ En tu biblioteca» y «Abrir ficha».
     */
    private void renderFooter() {
        previewFooter.getChildren().clear();
        boolean show = selected != null;
        previewFooter.setVisible(show);
        previewFooter.setManaged(show);
        if (!show) {
            return;
        }
        VisualNovel vn = selected.vn();
        if (inLibrary.contains(vn.id())) {
            Label done = new Label("✓  En tu biblioteca");
            done.getStyleClass().add("in-library");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button open = new Button("Abrir ficha");
            open.getStyleClass().add("preview-action");
            open.setOnAction(event -> navigator.showVnDetailFromSearch(vn.id()));
            HBox row = new HBox(16, done, spacer, open);
            row.setAlignment(Pos.CENTER_LEFT);
            previewFooter.getChildren().add(row);
        } else {
            Button add = new Button("Añadir a la biblioteca");
            add.getStyleClass().addAll("primary-button", "preview-action");
            add.setMaxWidth(Double.MAX_VALUE);
            add.setOnAction(event -> addToLibrary(vn, add));
            HBox.setHgrow(add, Priority.ALWAYS);
            Button view = new Button("Ver ficha");
            view.getStyleClass().add("preview-action");
            view.setMinWidth(Region.USE_PREF_SIZE);
            view.setOnAction(event -> openFicha(selected));
            HBox row = new HBox(12, add, view);
            row.setAlignment(Pos.CENTER_LEFT);
            previewFooter.getChildren().add(row);
        }
    }

    // =========================================================== utilidades

    private boolean isSensitive(VisualNovel vn) {
        return ctx.contentFilter().isSensitive(vn.imageSexual(), vn.imageViolence());
    }

    /** "TYPE-MOON · 2004" (solo el año, como en el diseño). */
    static String meta(VisualNovel vn) {
        String devs = vn.developerNames().isEmpty() ? "Desarrollador desconocido" : vn.developerNames();
        return devs + " · " + year(vn.released());
    }

    /** "2004-04-28" → "2004"; "TBA" se deja tal cual; null → "Sin fecha". */
    static String year(String released) {
        if (released == null || released.isBlank()) {
            return "Sin fecha";
        }
        return released.length() >= 4 && released.substring(0, 4).chars().allMatch(Character::isDigit)
                ? released.substring(0, 4) : released;
    }
}

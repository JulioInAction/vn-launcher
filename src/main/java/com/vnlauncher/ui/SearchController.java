package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vista "Buscar en VNDB": campo de búsqueda, resultados como tarjetas y vista previa.
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

    /** Lo que devuelve la tarea de búsqueda: resultados + ids que ya están en la biblioteca. */
    private record SearchResult(List<VisualNovel> results, Set<String> inLibrary) {
    }

    private final AppContext ctx;
    private final Navigator navigator;

    @FXML private TextField queryField;
    @FXML private Button searchButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;
    @FXML private VBox resultsBox;
    @FXML private VBox previewCard;
    @FXML private VBox noticeBox;
    @FXML private VBox emptyBox;
    @FXML private HBox resultsRow;

    /** Búsqueda en curso (para poder cancelarla desde el aviso de límite). */
    private Task<SearchResult> searchTask;
    /** Cuenta atrás del aviso de límite. */
    private Timeline countdown;

    private List<VisualNovel> results = List.of();
    /** Ids de VN que están en la biblioteca (para "✓ En tu biblioteca"). */
    private final Set<String> inLibrary = new HashSet<>();
    /** Tarjeta de cada resultado, para marcar la seleccionada. */
    private final Map<String, Node> cards = new HashMap<>();
    private VisualNovel selected;

    public SearchController(AppContext ctx, Navigator navigator) {
        this.ctx = ctx;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        setBusy(false);
        showEmpty(null);
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
                    results = result.results();
                    inLibrary.clear();
                    inLibrary.addAll(result.inLibrary());
                    selected = results.isEmpty() ? null : results.getFirst();
                    statusLabel.setText(results.size() == 1 ? "1 resultado" : results.size() + " resultados");
                    showEmpty(results.isEmpty() ? query : null);
                    renderResults();
                    renderPreview();
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
        // Sin resultados anteriores que enseñar, el aviso va solo (sin la tarjeta "Escribe un título...")
        emptyBox.setVisible(false);
        emptyBox.setManaged(false);
        boolean hasResults = results != null && !results.isEmpty();
        resultsRow.setVisible(hasResults);
        resultsRow.setManaged(hasResults);
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
        resultsRow.setVisible(!empty);
        resultsRow.setManaged(!empty);
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
                    ctx.libraryService().addToLibrary(vn);
                    return null;
                },
                ignored -> {
                    inLibrary.add(vn.id());
                    statusLabel.setText("\"" + vn.title() + "\" añadida a tu biblioteca");
                    renderResults();
                    renderPreview();
                    navigator.refreshSidebar(); // cambia el número de "Pendientes"
                },
                error -> {
                    statusLabel.setText("Error al añadir: " + error.getMessage());
                    renderResults();
                    renderPreview();
                });
    }

    // =========================================================== dibujo

    private void renderResults() {
        cards.clear();
        resultsBox.getChildren().clear();
        for (VisualNovel vn : results) {
            Node card = resultCard(vn);
            cards.put(vn.id(), card);
            resultsBox.getChildren().add(card);
        }
        markSelected();
    }

    /** Tarjeta de un resultado: miniatura · título y desarrollador/año · Añadir o ✓ En tu biblioteca. */
    private Node resultCard(VisualNovel vn) {
        CoverView thumb = new CoverView(vn.imageUrl(), isSensitive(vn), 56, 84, vn.title());

        Label title = new Label(vn.title());
        title.getStyleClass().add("result-title");
        Label meta = new Label(meta(vn));
        meta.getStyleClass().add("meta");
        VBox texts = new VBox(4, title, meta);
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0); // permite que los textos se corten con "..." si no caben

        Node action;
        if (inLibrary.contains(vn.id())) {
            Label done = new Label("✓  En tu biblioteca");
            done.getStyleClass().add("in-library");
            done.setMinWidth(Region.USE_PREF_SIZE);
            action = done;
        } else {
            Button add = new Button("Añadir");
            add.setOnAction(event -> addToLibrary(vn, add));
            add.setMinWidth(Region.USE_PREF_SIZE); // con un título largo, se corta el título y no el botón
            action = add;
        }

        HBox card = new HBox(16, thumb, texts, action);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("result-card");
        card.setOnMouseClicked(event -> {
            selected = vn;
            markSelected();
            renderPreview();
        });
        return card;
    }

    private void markSelected() {
        cards.forEach((id, card) -> {
            card.getStyleClass().remove("selected");
            if (selected != null && selected.id().equals(id)) {
                card.getStyleClass().add("selected");
            }
        });
    }

    /** Vista previa del resultado seleccionado y qué supone añadirlo. */
    private void renderPreview() {
        previewCard.getChildren().clear();
        if (selected == null) {
            Label hint = new Label(results.isEmpty() && statusLabel.getText().isEmpty()
                    ? "Escribe un título y pulsa Enter. Aquí verás los detalles del resultado que elijas."
                    : "Elige un resultado para ver sus detalles.");
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);
            previewCard.getChildren().add(hint);
            return;
        }
        VisualNovel vn = selected;
        boolean added = inLibrary.contains(vn.id());

        // Cabecera: portada + título y datos
        CoverView cover = new CoverView(vn.imageUrl(), isSensitive(vn), 94, 141, vn.title());
        Label title = new Label(vn.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        Label meta = new Label(meta(vn));
        meta.getStyleClass().add("meta");
        VBox headerTexts = new VBox(6, title, meta);
        if (vn.altTitle() != null) {
            Label alt = new Label(vn.altTitle());
            alt.getStyleClass().addAll("meta", "small");
            alt.setWrapText(true);
            headerTexts.getChildren().add(1, alt);
        }
        HBox header = new HBox(16, cover, headerTexts);

        // Descripción recortada a unas 5 líneas: con wrapText y alto máximo, JavaFX corta el
        // texto sobrante y pone "..." al final él solo.
        Label description = new Label(vn.description() == null ? "Sin descripción." : vn.description());
        description.getStyleClass().add("description");
        description.setWrapText(true);
        description.setTextOverrun(OverrunStyle.ELLIPSIS);
        description.setMaxHeight(5 * 21);

        Region divider = new Region();
        divider.getStyleClass().add("divider");

        Label heading = new Label(added ? "Ya está en tu biblioteca" : "Al añadirla");
        heading.getStyleClass().add("preview-heading");
        VBox explanation = new VBox(8, heading);
        if (!added) {
            for (String line : List.of(
                    "Los datos ya vienen en la búsqueda: 0 peticiones.",
                    "La portada se descarga una vez de t.vndb.org (no cuenta para el límite).",
                    "Los personajes se piden la primera vez que abras la ficha.")) {
                Label l = new Label(line);
                l.getStyleClass().add("text2");
                l.setWrapText(true);
                explanation.getChildren().add(l);
            }
        }

        Button action = new Button(added ? "Abrir ficha" : "Añadir a la biblioteca");
        action.getStyleClass().add("primary-button");
        action.setMaxWidth(Double.MAX_VALUE);
        action.setOnAction(event -> {
            if (added) {
                navigator.showVnDetail(vn.id());
            } else {
                addToLibrary(vn, action);
            }
        });

        previewCard.getChildren().addAll(header, description, divider, explanation, action);
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

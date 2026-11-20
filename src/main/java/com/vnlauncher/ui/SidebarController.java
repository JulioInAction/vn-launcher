package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.Theme;
import com.vnlauncher.service.LibraryService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Barra lateral común a todas las pantallas: logo, navegación numerada con marcapáginas,
 * estanterías (Jugando / Pendientes / Completadas), recientes, contador de VNDB y botón de tema.
 */
public class SidebarController {

    /** Secciones de la navegación, en orden. */
    public enum Section {
        LIBRARY("01", "Biblioteca"),
        SEARCH("02", "Buscar en VNDB"),
        SETTINGS("03", "Ajustes");

        final String number;
        final String label;

        Section(String number, String label) {
            this.number = number;
            this.label = label;
        }
    }

    /** Límite real de VNDB, el que se muestra al usuario (internamente nos quedamos en 190). */
    private static final int VNDB_LIMIT = 200;
    private static final int RECENT_COUNT = 3;

    // Iconos del botón de tema (caja de 24x24): sol y luna
    private static final String SUN = "M12 8.5A3.5 3.5 0 1 0 12.01 8.5Z M12 2.5V4.5 M12 19.5V21.5 M2.5 12H4.5 M19.5 12H21.5"
            + " M5.3 5.3L6.7 6.7 M17.3 17.3L18.7 18.7 M5.3 18.7L6.7 17.3 M17.3 6.7L18.7 5.3";
    private static final String MOON = "M20 14.5A8 8 0 1 1 9.5 4A6.5 6.5 0 0 0 20 14.5Z";

    private final AppContext ctx;
    private final Navigator navigator;

    @FXML private VBox watermark;
    @FXML private VBox navBox;
    @FXML private VBox shelvesBox;
    @FXML private HBox recentBox;
    @FXML private Label vndbCountLabel;
    @FXML private ProgressBar vndbCountBar;
    @FXML private Button themeButton;

    private final Map<Section, Button> navButtons = new EnumMap<>(Section.class);
    private final Map<PlayStatus, Label> shelfCounts = new EnumMap<>(PlayStatus.class);

    public SidebarController(AppContext ctx, Navigator navigator) {
        this.ctx = ctx;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        // Texto en vertical: una etiqueta por letra, cada una de 59 px de alto. (Un solo Label con
        // saltos de línea dejaba demasiado espacio entre letras y JavaFX no admite interlineado negativo.)
        for (String letter : "ビジュアルノベル".split("")) {
            Label l = new Label(letter);
            l.getStyleClass().add("watermark");
            l.setMinHeight(59);
            l.setPrefHeight(59);
            l.setMaxHeight(59);
            watermark.getChildren().add(l);
        }

        for (Section section : Section.values()) {
            Button button = navItem(section);
            navButtons.put(section, button);
            navBox.getChildren().add(button);
        }
        for (PlayStatus shelf : PlayStatus.values()) {
            shelvesBox.getChildren().add(shelfItem(shelf));
        }
        Tooltip.install(vndbCountLabel, new Tooltip("Datos guardados en " + ctx.paths().root()));
        startVndbCounter();
    }

    // ------------------------------------------------------------------ construcción

    /** Entrada de navegación: [marcapáginas] 01 Biblioteca. El marcapáginas solo se ve si está activa. */
    private Button navItem(Section section) {
        Region bookmark = new Region();
        bookmark.getStyleClass().add("bookmark");
        Label number = new Label(section.number);
        number.getStyleClass().add("nav-number");
        Label text = new Label(section.label);
        text.getStyleClass().add("nav-text");

        HBox content = new HBox(0, bookmark, spacer(18), number, spacer(10), text);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button(null, content);
        button.getStyleClass().setAll("nav-item"); // setAll: sin el estilo general de .button
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> {
            switch (section) {
                case LIBRARY -> navigator.showLibrary();
                case SEARCH -> navigator.showSearch();
                case SETTINGS -> navigator.showSettings();
            }
        });
        return button;
    }

    /** Estantería: ● Jugando ........ 2 */
    private Button shelfItem(PlayStatus shelf) {
        Circle dot = new Circle(3.5);
        dot.getStyleClass().add(switch (shelf) {
            case PLAYING -> "dot-playing";
            case PENDING -> "dot-pending";
            case COMPLETED -> "dot-completed";
        });
        Label name = new Label(shelf.shelfName());
        Label count = new Label("0");
        count.getStyleClass().add("shelf-count");
        shelfCounts.put(shelf, count);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        HBox content = new HBox(12, dot, name, grow, count);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button(null, content);
        button.getStyleClass().setAll("shelf-item");
        button.setMaxWidth(Double.MAX_VALUE);
        // Que el HBox interior ocupe todo el ancho del botón (si no, el número no se va a la derecha)
        content.prefWidthProperty().bind(button.widthProperty().subtract(34));
        button.setOnAction(event -> navigator.showLibrary(shelf));
        return button;
    }

    private static Region spacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        return r;
    }

    // ------------------------------------------------------------------ estado

    /** Marca la sección activa (texto en color normal + marcapáginas rosa). */
    public void setActive(Section active) {
        navButtons.forEach((section, button) -> {
            button.getStyleClass().remove("active");
            if (section == active) {
                button.getStyleClass().add("active");
            }
        });
    }

    /** Vuelve a calcular estanterías y recientes (en segundo plano: consulta la BD). */
    public void refresh() {
        BackgroundTasks.run(ctx.background(), () -> ctx.libraryService().findAllWithStatus(),
                this::render,
                error -> { /* la barra no es crítica: si falla, se queda como estaba */ });
    }

    private void render(List<LibraryItem> items) {
        for (PlayStatus shelf : PlayStatus.values()) {
            long n = items.stream().filter(i -> i.status() == shelf).count();
            shelfCounts.get(shelf).setText(String.valueOf(n));
        }

        recentBox.getChildren().clear();
        List<LibraryEntry> recent = LibraryService.recentlyPlayed(items, RECENT_COUNT);
        if (recent.isEmpty()) {
            Label none = new Label("Aún no has jugado a nada.");
            none.getStyleClass().addAll("muted", "small");
            recentBox.getChildren().add(none);
        }
        for (LibraryEntry entry : recent) {
            var vn = entry.vn();
            Path file = vn.imagePath() == null ? null : ctx.imageCache().resolve(vn.imagePath());
            CoverView cover = new CoverView(file,
                    ctx.contentFilter().isSensitive(vn.imageSexual(), vn.imageViolence()), 48, 72, vn.title());
            cover.getStyleClass().add("recent-cover");
            Tooltip.install(cover, new Tooltip(vn.title()));
            cover.setOnMouseClicked(event -> navigator.showVnDetail(vn.id()));
            recentBox.getChildren().add(cover);
        }
    }

    /**
     * Actualiza el contador de peticiones cada 2 segundos. Un Timeline es un temporizador de
     * JavaFX: sus KeyFrame se ejecutan en el hilo de la interfaz, así que puede tocar los Label.
     * (recentCount() no bloquea nunca; ver RateLimiter.)
     */
    private void startVndbCounter() {
        Runnable update = () -> {
            int count = ctx.rateLimiter().recentCount();
            vndbCountLabel.setText(count + "/" + VNDB_LIMIT);
            vndbCountBar.setProgress((double) count / VNDB_LIMIT);
        };
        update.run();
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> update.run()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    // ------------------------------------------------------------------ tema

    /** Pone el texto y el icono del botón según el tema ACTUAL (ofrece cambiar al otro). */
    public void showTheme(Theme current) {
        boolean dark = current == Theme.DARK;
        SVGPath icon = new SVGPath();
        icon.setContent(dark ? SUN : MOON);
        icon.getStyleClass().add("icon-stroke");
        themeButton.setGraphic(icon);
        themeButton.setText(dark ? "Tema claro" : "Tema oscuro");
    }

    @FXML
    private void onToggleTheme() {
        Theme next = Themes.current(themeButton).toggled();
        Themes.apply(themeButton.getScene().getRoot(), next);
        showTheme(next);
        // Guardar en la tabla setting (clave "theme") en segundo plano
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.settingsService().setTheme(next);
                    return null;
                },
                ignored -> { },
                error -> Dialogs.error(themeButton, "No se pudo guardar el tema: " + error.getMessage()));
    }
}

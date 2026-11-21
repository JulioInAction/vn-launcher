package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Barra lateral común a todas las pantallas (docs/diseño/capturas/biblioteca-1-0-*.png): logo,
 * navegación numerada con marcapáginas, el bloque «Estado» (los cinco estados con su contador) y el
 * contador de peticiones a VNDB.
 */
public class SidebarController {

    /** Secciones de la navegación, en orden. */
    public enum Section {
        LIBRARY("01", "Biblioteca"),
        SEARCH("02", "Buscar"),
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

    private final AppContext ctx;
    private final Navigator navigator;

    @FXML private StackPane sidebarRoot;
    @FXML private ImageView watermark;
    @FXML private VBox navBox;
    @FXML private VBox statusBox;
    @FXML private Label vndbCountLabel;
    @FXML private ProgressBar vndbCountBar;

    private final Map<Section, Button> navButtons = new EnumMap<>(Section.class);
    private final Map<PlayStatus, Label> statusCounts = new EnumMap<>(PlayStatus.class);
    private final Map<PlayStatus, Button> statusButtons = new EnumMap<>(PlayStatus.class);

    // Marca de agua de cada tema (se cargan una vez y se cambian al cambiar de tema)
    private final Image watermarkDark = loadImage("images/marca-agua-rosa.png");
    private final Image watermarkLight = loadImage("images/marca-agua-rosa-claro.png");

    public SidebarController(AppContext ctx, Navigator navigator) {
        this.ctx = ctx;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        // La marca de agua no está "managed": el StackPane no la coloca. La pegamos al borde
        // derecho enlazando su X al ancho de la barra (si la barra cambia de ancho, se recoloca sola).
        // Math.round: la imagen mide 57,94 px de ancho y, como el padre no coloca este nodo, nadie
        // redondearía su posición; en una X con decimales se vería borrosa.
        watermark.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (double) Math.round(sidebarRoot.getWidth() - watermark.getLayoutBounds().getWidth()),
                sidebarRoot.widthProperty(), watermark.layoutBoundsProperty()));

        for (Section section : Section.values()) {
            Button button = navItem(section);
            navButtons.put(section, button);
            navBox.getChildren().add(button);
        }
        for (PlayStatus status : PlayStatus.values()) {
            Button button = statusItem(status);
            statusButtons.put(status, button);
            statusBox.getChildren().add(button);
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

    /** Estado: ● Jugando ........ 2. Al pulsarlo, la biblioteca solo con las VNs en ese estado. */
    private Button statusItem(PlayStatus status) {
        Circle dot = StatusVisuals.dot(status);
        Label name = new Label(status.shelfName());
        Label count = new Label("0");
        count.getStyleClass().add("shelf-count");
        statusCounts.put(status, count);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        HBox content = new HBox(12, dot, name, grow, count);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button(null, content);
        button.getStyleClass().setAll("shelf-item");
        button.setMaxWidth(Double.MAX_VALUE);
        // Que el HBox interior ocupe todo el ancho del botón (si no, el número no se va a la derecha)
        content.prefWidthProperty().bind(button.widthProperty().subtract(34));
        button.setOnAction(event -> navigator.showLibrary(status));
        return button;
    }

    private static Region spacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        return r;
    }

    // ------------------------------------------------------------------ estado

    /**
     * Marca la sección activa (texto en color normal + marcapáginas rosa). Fuera de la biblioteca
     * (Buscar, Ajustes) no hay ningún estado filtrado que resaltar.
     */
    public void setActive(Section active) {
        navButtons.forEach((section, button) -> {
            button.getStyleClass().remove("active");
            if (section == active) {
                button.getStyleClass().add("active");
            }
        });
        if (active != Section.LIBRARY) {
            setActiveStatus(null);
        }
    }

    /** Resalta el estado por el que se filtra la biblioteca (null = ninguno). */
    public void setActiveStatus(PlayStatus active) {
        statusButtons.forEach((status, button) -> {
            button.getStyleClass().remove("active");
            if (status == active) {
                button.getStyleClass().add("active");
            }
        });
    }

    /** Vuelve a contar las VNs de cada estado (en segundo plano: consulta la BD). */
    public void refresh() {
        BackgroundTasks.run(ctx.background(), () -> ctx.libraryService().findAllWithStatus(),
                this::render,
                error -> { /* la barra no es crítica: si falla, se queda como estaba */ });
    }

    private void render(List<LibraryItem> items) {
        for (PlayStatus status : PlayStatus.values()) {
            long n = items.stream().filter(i -> i.status() == status).count();
            statusCounts.get(status).setText(String.valueOf(n));
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

    /** Adapta la barra al tema ACTUAL: la marca de agua tiene una imagen para cada tema. */
    public void showTheme(Theme current) {
        watermark.setImage(current == Theme.DARK ? watermarkDark : watermarkLight);
    }

    private static Image loadImage(String resource) {
        return new Image(SidebarController.class.getResource(resource).toExternalForm());
    }

    /** Lo conecta MainController. */
    void setThemeManager(ThemeManager themeManager) {
        // Cada vez que cambie el tema que se ve (Ajustes o Windows), adaptar la barra
        themeManager.addListener(this::showTheme);
    }
}

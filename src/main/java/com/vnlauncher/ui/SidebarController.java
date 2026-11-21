package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Barra lateral común a todas las pantallas (docs/diseño/capturas/biblioteca-1-0-*.png): logo,
 * navegación con marcapáginas, el bloque «Estado» (los cinco estados con su contador), «Mis
 * estanterías» y el contador de peticiones a VNDB.
 *
 * <p>Las estanterías se reordenan arrastrándolas, y con clic derecho → «Editar…» se cambian
 * (nombre, color, sitio con «Subir»/«Bajar») o se eliminan.
 */
public class SidebarController {

    /** Secciones de la navegación, en orden. */
    public enum Section {
        LIBRARY("Biblioteca"),
        SEARCH("Buscar"),
        SETTINGS("Ajustes");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    /** Lo que se lee en segundo plano para pintar los contadores y las estanterías. */
    private record Counts(List<LibraryItem> items, List<Shelf> shelves) {
    }

    /** Límite real de VNDB, el que se muestra al usuario (internamente nos quedamos en 190). */
    private static final int VNDB_LIMIT = 200;
    private static final String NEW_SHELF = "Nueva estantería";
    private static final String EDIT = "Editar…";
    private static final String DELETE_TITLE = "¿Eliminar «%s»?";
    private static final String DELETE_TEXT = "Las novelas no se borran: solo salen de esta estantería.";

    private final AppContext ctx;
    private final Navigator navigator;

    @FXML private StackPane sidebarRoot;
    @FXML private ImageView watermark;
    @FXML private VBox navBox;
    @FXML private VBox statusBox;
    @FXML private VBox shelvesBox;
    @FXML private Button addShelfButton;
    @FXML private Label vndbCountLabel;
    @FXML private ProgressBar vndbCountBar;

    private final Map<Section, Button> navButtons = new EnumMap<>(Section.class);
    private final Map<PlayStatus, Label> statusCounts = new EnumMap<>(PlayStatus.class);
    private final Map<PlayStatus, Button> statusButtons = new EnumMap<>(PlayStatus.class);
    /** Las estanterías que se ven, en orden (para arrastrar y para los diálogos). */
    private List<Shelf> shelves = List.of();
    /** Lo que se está filtrando en la biblioteca, para resaltarlo (null = nada). */
    private PlayStatus activeStatus;
    private Long activeShelfId;

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
        addShelfButton.setTooltip(new Tooltip(NEW_SHELF));
        addShelfButton.setOnAction(event -> createShelf());
        Tooltip.install(vndbCountLabel, new Tooltip("Datos guardados en " + ctx.paths().root()));
        startVndbCounter();
    }

    // ------------------------------------------------------------------ construcción

    /**
     * Entrada de navegación: [marcapáginas] Biblioteca. El marcapáginas solo se ve si está activa.
     * El texto empieza a la misma altura que «ESTADO» y los puntos de los estados (26 px).
     */
    private Button navItem(Section section) {
        Region bookmark = new Region();
        bookmark.getStyleClass().add("bookmark");
        Label text = new Label(section.label);
        text.getStyleClass().add("nav-text");

        HBox content = new HBox(0, bookmark, spacer(18), text);
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
        Label count = new Label("0");
        statusCounts.put(status, count);
        Button button = listItem(StatusVisuals.dot(status), status.shelfName(), count);
        button.setOnAction(event -> navigator.showLibrary(status));
        return button;
    }

    /** Estantería: ■ Favoritas ........ 3. Se arrastra para reordenar; clic derecho para editar. */
    private Button shelfItem(Shelf shelf, int index) {
        Button button = listItem(ShelfDialog.colorSquare(shelf.color(), "shelf-color"), shelf.name(),
                new Label(String.valueOf(shelf.novels())));
        button.setTooltip(new Tooltip(shelf.name())); // por si el nombre no cabe entero
        button.setOnAction(event -> navigator.showLibraryWithShelf(shelf));
        MenuItem edit = new MenuItem(EDIT);
        edit.setOnAction(event -> editShelf(shelf));
        button.setContextMenu(new ContextMenu(edit));
        if (activeShelfId != null && activeShelfId == shelf.id()) {
            button.getStyleClass().add("active");
        }
        enableDragAndDrop(button, shelf, index);
        return button;
    }

    /** Fila común de estados y estanterías: [icono] Nombre ........ número. */
    private static Button listItem(Node icon, String name, Label count) {
        Label nameLabel = new Label(name);
        nameLabel.setMinWidth(0); // un nombre largo se corta con «…» y el número sigue viéndose
        count.getStyleClass().add("shelf-count");
        count.setMinWidth(Region.USE_PREF_SIZE);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        // El icono en un hueco fijo: el punto y el cuadradito miden distinto, los nombres van alineados
        StackPane iconBox = new StackPane(icon);
        iconBox.setMinWidth(8);
        iconBox.setPrefWidth(8);

        HBox content = new HBox(10, iconBox, nameLabel, grow, count);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button(null, content);
        button.getStyleClass().setAll("shelf-item");
        button.setMaxWidth(Double.MAX_VALUE);
        // Que el HBox interior ocupe todo el ancho del botón (si no, el número no se va a la derecha).
        // 30 = el margen del botón a izquierda y derecha (26 + 4, ver .shelf-item)
        content.prefWidthProperty().bind(button.widthProperty().subtract(30));
        content.maxWidthProperty().bind(content.prefWidthProperty());
        return button;
    }

    private static Region spacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        return r;
    }

    // ------------------------------------------------------------------ estanterías

    /** «+» o «+ Nueva estantería». */
    @FXML
    private void createShelf() {
        ShelfDialog.create(sidebarRoot, shelves).ifPresent(result ->
                save(() -> ctx.shelfService().create(result.name(), result.color())));
    }

    private void editShelf(Shelf shelf) {
        ShelfDialog.edit(sidebarRoot, shelf, shelves).ifPresent(result -> {
            if (result.delete()) {
                if (Dialogs.confirm(sidebarRoot, String.format(DELETE_TITLE, shelf.name()), DELETE_TEXT,
                        ShelfDialog.DELETE, true)) {
                    save(() -> {
                        ctx.shelfService().delete(shelf.id());
                        return null;
                    });
                }
                return;
            }
            save(() -> {
                ctx.shelfService().update(shelf, result.name(), result.color());
                if (result.position() != shelf.position()) {
                    ctx.shelfService().moveTo(shelf.id(), result.position());
                }
                return null;
            });
        });
    }

    /** Guarda en segundo plano y avisa a todos (barra, biblioteca, ficha) de que han cambiado. */
    private void save(java.util.concurrent.Callable<?> work) {
        BackgroundTasks.run(ctx.background(), work, done -> navigator.shelvesChanged(),
                "No se ha podido guardar la estantería.");
    }

    /**
     * Arrastrar y soltar para reordenar, igual que las rutas (ver RoutesPane): se lleva el id en el
     * Dragboard, se marca con una línea si caerá encima o debajo, y al soltar se guarda la posición.
     */
    private void enableDragAndDrop(Button row, Shelf shelf, int index) {
        row.setOnDragDetected(event -> {
            Dragboard dragboard = row.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("shelf:" + shelf.id());
            dragboard.setContent(content);
            dragboard.setDragView(row.snapshot(null, null), event.getX(), event.getY());
            event.consume();
        });
        row.setOnDragOver(event -> {
            if (event.getGestureSource() != row && isShelfDrag(event.getDragboard())) {
                event.acceptTransferModes(TransferMode.MOVE);
                boolean above = event.getY() < row.getHeight() / 2;
                row.getStyleClass().removeAll("drop-above", "drop-below");
                row.getStyleClass().add(above ? "drop-above" : "drop-below");
            }
            event.consume();
        });
        row.setOnDragExited(event -> row.getStyleClass().removeAll("drop-above", "drop-below"));
        row.setOnDragDropped(event -> {
            long draggedId = Long.parseLong(event.getDragboard().getString().substring("shelf:".length()));
            int draggedIndex = indexOf(draggedId);
            boolean above = event.getY() < row.getHeight() / 2;
            int target = above ? index : index + 1;
            // Al sacar la arrastrada de la lista, las que estaban detrás suben un puesto
            if (draggedIndex < target) {
                target--;
            }
            int finalTarget = target;
            if (draggedIndex >= 0 && draggedIndex != finalTarget) {
                save(() -> {
                    ctx.shelfService().moveTo(draggedId, finalTarget);
                    return null;
                });
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private static boolean isShelfDrag(Dragboard dragboard) {
        return dragboard.hasString() && dragboard.getString().startsWith("shelf:");
    }

    private int indexOf(long shelfId) {
        for (int i = 0; i < shelves.size(); i++) {
            if (shelves.get(i).id() == shelfId) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ estado

    /**
     * Marca la sección activa (texto en color normal + marcapáginas rosa). Fuera de la biblioteca
     * (Buscar, Ajustes) no hay ningún filtro que resaltar.
     */
    public void setActive(Section active) {
        navButtons.forEach((section, button) -> {
            button.getStyleClass().remove("active");
            if (section == active) {
                button.getStyleClass().add("active");
            }
        });
        if (active != Section.LIBRARY) {
            setActiveFilter(null, null);
        }
    }

    /**
     * Resalta lo que se está filtrando en la biblioteca: un estado o una estantería (nunca los dos).
     *
     * @param shelfId la estantería, o null
     */
    public void setActiveFilter(PlayStatus status, Long shelfId) {
        activeStatus = status;
        activeShelfId = shelfId;
        statusButtons.forEach((s, button) -> {
            button.getStyleClass().remove("active");
            if (s == activeStatus) {
                button.getStyleClass().add("active");
            }
        });
        renderShelves();
    }

    /** Vuelve a contar las VNs de cada estado y a leer las estanterías (en segundo plano). */
    public void refresh() {
        BackgroundTasks.run(ctx.background(),
                () -> new Counts(ctx.libraryService().findAllWithStatus(), ctx.shelfService().findAll()),
                this::render,
                error -> { /* la barra no es crítica: si falla, se queda como estaba */ });
    }

    private void render(Counts counts) {
        for (PlayStatus status : PlayStatus.values()) {
            long n = counts.items().stream().filter(i -> i.status() == status).count();
            statusCounts.get(status).setText(String.valueOf(n));
        }
        shelves = counts.shelves();
        renderShelves();
    }

    private void renderShelves() {
        shelvesBox.getChildren().clear();
        for (int i = 0; i < shelves.size(); i++) {
            shelvesBox.getChildren().add(shelfItem(shelves.get(i), i));
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

package com.vnlauncher.ui;

import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.RouteService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Sección "Rutas" de la ficha: título, botón para añadir y la lista de rutas en el orden
 * recomendado. Cada fila: asa para arrastrar, número, avatar de la heroína, nombre/notas,
 * estado (desplegable con aspecto de pastilla) y botón de editar. Clic derecho: más opciones.
 *
 * <p>Es un componente hecho en código (extiende VBox) en vez de FXML: así VnDetailController
 * no crece sin control y este panel se puede entender por separado.
 */
class RoutesPane extends VBox {

    private static final double AVATAR_SIZE = 40;

    private final RouteService routeService;
    private final ExecutorService background;
    private final ImageCacheService images;
    private final ContentFilter contentFilter;
    private final String vnId;

    private final Label messageLabel = new Label();
    private final VBox list = new VBox(8);

    /** Personajes por id (LinkedHashMap conserva el orden de inserción). */
    private final Map<String, VnCharacter> charactersById = new LinkedHashMap<>();
    private boolean includeSpoilers = false;
    private List<Route> routes = List.of();
    private Consumer<List<Route>> onRoutesChanged = r -> { };
    private String vnTitle = "";

    RoutesPane(RouteService routeService, ExecutorService background, ImageCacheService images,
               ContentFilter contentFilter, String vnId) {
        super(12);
        this.routeService = routeService;
        this.background = background;
        this.images = images;
        this.contentFilter = contentFilter;
        this.vnId = vnId;

        Label title = new Label("Rutas");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label("orden recomendado");
        subtitle.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addButton = new Button("+  Añadir ruta");
        addButton.getStyleClass().add("secondary-button");
        addButton.setOnAction(event -> openCreateDialog());
        HBox header = new HBox(10, title, subtitle, spacer, addButton);
        header.setAlignment(Pos.BASELINE_LEFT);

        messageLabel.getStyleClass().add("muted");
        messageLabel.setWrapText(true);
        getChildren().addAll(header, messageLabel, list);
    }

    /** Se avisa cada vez que cambia la lista de rutas (para actualizar personajes y progreso). */
    void setOnRoutesChanged(Consumer<List<Route>> listener) {
        this.onRoutesChanged = listener;
    }

    /**
     * Personajes disponibles para elegir como heroína y para los avatares.
     *
     * @param showSpoilers si false, los personajes spoiler no se ofrecen en el diálogo
     */
    void setCharacters(List<VnCharacter> characters, boolean showSpoilers) {
        charactersById.clear();
        characters.forEach(vc -> charactersById.put(vc.character().id(), vc));
        includeSpoilers = showSpoilers;
        render();
    }

    /** Vuelve a leer las rutas de la BD y redibuja. */
    void reload() {
        runThenReload(() -> null);
    }

    /** Creación rápida desde la tarjeta de un personaje: "este personaje tiene ruta". */
    void addRouteFor(VnCharacter vc) {
        runThenReload(() -> routeService.addRoute(vnId, vc.character().id(), vc.character().name(), null));
    }

    // ---------------------------------------------------------------- acciones

    /**
     * Ejecuta {@code work} (que modifica rutas) en segundo plano, luego relee la lista
     * y la pinta. Todas las acciones de este panel pasan por aquí.
     */
    private void runThenReload(Callable<?> work) {
        BackgroundTasks.run(background, () -> {
                    work.call();
                    return routeService.routesOf(vnId);
                },
                loaded -> {
                    routes = loaded;
                    render();
                    onRoutesChanged.accept(loaded);
                },
                "No se ha podido guardar la ruta.");
    }

    /** Título de la VN, para el subtítulo del diálogo de ruta. */
    void setVnTitle(String vnTitle) {
        this.vnTitle = vnTitle;
    }

    /** Diálogo "Nueva ruta" (también lo usa el estado vacío de personajes de la ficha). */
    void openCreateDialog() {
        RouteDialog.show(this, vnTitle, null, selectableCharacters(null), routes.size(), images, contentFilter)
                .ifPresent(result -> runThenReload(() -> {
                    Route created = routeService.addRoute(vnId, result.characterId(), result.name(), result.notes());
                    if (result.status() != RouteStatus.PENDING) {
                        routeService.setStatus(created.id(), result.status());
                    }
                    if (result.position() != created.recommendedOrder()) {
                        routeService.moveTo(created.id(), result.position() - 1); // moveTo empieza en 0
                    }
                    return null;
                }));
    }

    private void openEditDialog(Route route) {
        RouteDialog.show(this, vnTitle, route, selectableCharacters(route), routes.size(), images, contentFilter)
                .ifPresent(result -> {
                    if (result.delete()) {
                        confirmDelete(route);
                        return;
                    }
                    runThenReload(() -> {
                        routeService.updateRoute(new Route(route.id(), vnId, result.characterId(),
                                result.name(), route.recommendedOrder(), result.status(), result.notes(),
                                route.completedAt()));
                        if (result.position() != route.recommendedOrder()) {
                            routeService.moveTo(route.id(), result.position() - 1);
                        }
                        return null;
                    });
                });
    }

    private void confirmDelete(Route route) {
        if (Dialogs.confirm(this, "¿Eliminar la ruta \"" + route.name() + "\"?",
                "Se perderán su estado y sus notas.", "Eliminar", true)) {
            runThenReload(() -> {
                routeService.delete(route.id());
                return null;
            });
        }
    }

    /**
     * Personajes para el desplegable del diálogo: sin los spoiler (salvo que se muestren),
     * pero incluyendo siempre la heroína actual de la ruta que se edita.
     */
    private List<VnCharacter> selectableCharacters(Route editing) {
        List<VnCharacter> result = new ArrayList<>();
        for (VnCharacter vc : charactersById.values()) {
            boolean isCurrent = editing != null && vc.character().id().equals(editing.characterId());
            if (!vc.isSpoiler() || includeSpoilers || isCurrent) {
                result.add(vc);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- dibujo

    private void render() {
        boolean empty = routes.isEmpty();
        messageLabel.setText(empty
                ? "Aún no hay rutas. Márcalas en los personajes (\"Marcar con ruta\") o añade una "
                  + "con el botón, también rutas sin heroína como la común o el true end."
                : "Arrastra las rutas para cambiar el orden. Clic derecho para más opciones.");
        list.getChildren().clear();
        for (int i = 0; i < routes.size(); i++) {
            list.getChildren().add(createRow(routes.get(i), i));
        }
    }

    private HBox createRow(Route route, int index) {
        Label handle = new Label("⠿");
        handle.getStyleClass().add("drag-handle");
        Label order = new Label(String.valueOf(route.recommendedOrder()));
        order.getStyleClass().add("route-order");

        // Avatar de la heroína (con su imagen o sus iniciales)
        VnCharacter vc = route.characterId() == null ? null : charactersById.get(route.characterId());
        Node avatar;
        if (vc != null) {
            GameCharacter c = vc.character();
            Path file = c.imagePath() == null ? null : images.resolve(c.imagePath());
            avatar = CoverView.avatar(file, contentFilter.isSensitive(c.imageSexual(), c.imageViolence()),
                    AVATAR_SIZE, c.name());
        } else {
            Region empty = new Region();
            empty.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
            avatar = empty;
        }

        // Textos: nombre, heroína (si se llama distinto que la ruta) y notas. Solo se añade lo que
        // tiene texto: una etiqueta vacía ocuparía su hueco y subiría el nombre
        Label name = new Label(route.name());
        name.getStyleClass().add("route-name");
        VBox texts = new VBox(2, name);
        // Centrado en vertical: el HBox estira este VBox a todo el alto de la fila (lo marcan el
        // desplegable y el botón, de 44 px) y, sin esto, el nombre se quedaría arriba
        texts.setAlignment(Pos.CENTER_LEFT);
        String heroine = route.characterId() == null ? "Sin heroína"
                : vc != null ? vc.character().name() : "Personaje " + route.characterId();
        if (!heroine.equals(route.name())) {
            Label sub = new Label(heroine);
            sub.getStyleClass().add("muted");
            texts.getChildren().add(sub);
        }
        if (route.notes() != null && !route.notes().isBlank()) {
            Label notes = new Label(route.notes());
            notes.setWrapText(true);
            notes.getStyleClass().add("route-notes");
            texts.getChildren().add(notes);
        }
        HBox.setHgrow(texts, Priority.ALWAYS);

        Button edit = new Button(null, Icons.pencil());
        edit.getStyleClass().add("icon-button");
        edit.setTooltip(new Tooltip("Editar o borrar la ruta"));
        edit.setOnAction(e -> openEditDialog(route));

        HBox row = new HBox(14, handle, order, avatar, texts, statusBox(route), edit);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("route-row");
        row.setOnContextMenuRequested(e ->
                contextMenu(route, index).show(row, e.getScreenX(), e.getScreenY()));
        enableDragAndDrop(row, route, index);
        return row;
    }

    /**
     * Desplegable de estado con aspecto de pastilla del color del estado. La pastilla es el
     * desplegable entero (texto y flecha ▼ dentro), no solo su texto: ver .status-box en styles.css.
     */
    private ComboBox<RouteStatus> statusBox(Route route) {
        ComboBox<RouteStatus> status = new ComboBox<>();
        status.getItems().setAll(RouteStatus.values());
        status.setCellFactory(l -> new StatusCell());
        status.setButtonCell(new StatusCell());
        status.setValue(route.status());
        status.getStyleClass().addAll("status-box", pillClass(route.status()));

        // Guardar el estado. Ojo: con el desplegable ABIERTO, las flechas del teclado cambian el
        // valor a cada pulsación. Si guardáramos en cada cambio, la lista se redibujaría a mitad
        // de la elección. Por eso: con la lista abierta, se guarda al cerrarla; con la lista
        // cerrada (rueda del ratón, teclado), se guarda en el momento.
        // Los listeners se añaden DESPUÉS de setValue para que el valor inicial no cuente.
        boolean[] saved = {false}; // array: una lambda no puede reasignar una variable local
        Runnable commit = () -> {
            RouteStatus chosen = status.getValue();
            if (!saved[0] && chosen != null && chosen != route.status()) {
                saved[0] = true;
                runThenReload(() -> routeService.setStatus(route.id(), chosen));
            }
        };
        status.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!status.isShowing()) commit.run();
        });
        status.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (!isShowing) commit.run();
        });
        return status;
    }

    /** Clase CSS de pastilla para cada estado: pill-pending, pill-in-progress, pill-completed. */
    static String pillClass(RouteStatus status) {
        return "pill-" + status.name().toLowerCase().replace('_', '-');
    }

    private ContextMenu contextMenu(Route route, int index) {
        MenuItem up = new MenuItem("Subir");
        up.setDisable(index == 0);
        up.setOnAction(e -> runThenReload(() -> {
            routeService.moveUp(route.id());
            return null;
        }));
        MenuItem down = new MenuItem("Bajar");
        down.setDisable(index == routes.size() - 1);
        down.setOnAction(e -> runThenReload(() -> {
            routeService.moveDown(route.id());
            return null;
        }));
        MenuItem edit = new MenuItem("Editar...");
        edit.setOnAction(e -> openEditDialog(route));
        MenuItem delete = new MenuItem("Borrar...");
        delete.setOnAction(e -> confirmDelete(route));
        return new ContextMenu(up, down, new SeparatorMenuItem(), edit, delete);
    }

    /**
     * Arrastrar y soltar para reordenar. Funciona con eventos:
     * <ol>
     *   <li>DragDetected: el usuario empieza a arrastrar una fila → guardamos su id en el
     *       "portapapeles" del arrastre (Dragboard).</li>
     *   <li>DragOver: pasa por encima de otra fila → aceptamos y marcamos con una línea
     *       si se soltará encima o debajo de ella.</li>
     *   <li>DragDropped: la suelta → calculamos la nueva posición y se la pedimos al servicio.</li>
     * </ol>
     */
    private void enableDragAndDrop(HBox row, Route route, int index) {
        row.setOnDragDetected(event -> {
            Dragboard dragboard = row.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(route.id()));
            dragboard.setContent(content);
            // "Fantasma" de la fila bajo el ratón, anclado al punto donde se agarró
            dragboard.setDragView(row.snapshot(null, null), event.getX(), event.getY());
            row.getStyleClass().add("dragging");
            event.consume();
        });
        row.setOnDragOver(event -> {
            if (event.getGestureSource() != row && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
                boolean above = event.getY() < row.getHeight() / 2;
                row.getStyleClass().removeAll("drop-above", "drop-below");
                row.getStyleClass().add(above ? "drop-above" : "drop-below");
            }
            event.consume();
        });
        row.setOnDragExited(event -> row.getStyleClass().removeAll("drop-above", "drop-below"));
        row.setOnDragDropped(event -> {
            long draggedId = Long.parseLong(event.getDragboard().getString());
            int draggedIndex = indexOf(draggedId);
            boolean above = event.getY() < row.getHeight() / 2;
            int target = above ? index : index + 1;
            // Al sacar la fila arrastrada de la lista, las que estaban detrás suben un puesto
            if (draggedIndex < target) {
                target--;
            }
            int finalTarget = target;
            if (draggedIndex != finalTarget) {
                runThenReload(() -> {
                    routeService.moveTo(draggedId, finalTarget);
                    return null;
                });
            }
            event.setDropCompleted(true);
            event.consume();
        });
        row.setOnDragDone(event -> row.getStyleClass().remove("dragging"));
    }

    private int indexOf(long routeId) {
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).id() == routeId) {
                return i;
            }
        }
        return -1;
    }

    /** Celda del desplegable de estados: muestra "En curso" en vez de "IN_PROGRESS". */
    static class StatusCell extends javafx.scene.control.ListCell<RouteStatus> {
        @Override
        protected void updateItem(RouteStatus status, boolean empty) {
            super.updateItem(status, empty);
            setText(empty || status == null ? null : status.label());
        }
    }
}

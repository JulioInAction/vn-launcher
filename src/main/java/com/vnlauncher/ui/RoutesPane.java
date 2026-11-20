package com.vnlauncher.ui;

import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteProgress;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.RouteService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
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
 * Contenido de la pestaña "Rutas": progreso, botón para añadir y la lista de rutas en el orden
 * recomendado, cada una con su estado, notas y botones para reordenar, editar y borrar.
 *
 * <p>Es un componente hecho en código (extiende VBox) en vez de FXML: así VnDetailController
 * no crece sin control y este panel se puede entender por separado.
 */
class RoutesPane extends VBox {

    private static final double THUMB_WIDTH = 48;
    private static final double THUMB_HEIGHT = 68;

    private final RouteService routeService;
    private final ExecutorService background;
    private final ImageCacheService images;
    private final ContentFilter contentFilter;
    private final String vnId;

    private final Label progressLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label messageLabel = new Label();
    private final VBox list = new VBox(8);

    /** Personajes por id (LinkedHashMap conserva el orden de inserción). */
    private final Map<String, VnCharacter> charactersById = new LinkedHashMap<>();
    private boolean includeSpoilers = false;
    private List<Route> routes = List.of();
    private Consumer<List<Route>> onRoutesChanged = r -> { };

    RoutesPane(RouteService routeService, ExecutorService background, ImageCacheService images,
               ContentFilter contentFilter, String vnId) {
        super(12);
        this.routeService = routeService;
        this.background = background;
        this.images = images;
        this.contentFilter = contentFilter;
        this.vnId = vnId;

        Button addButton = new Button("+ Añadir ruta");
        addButton.setDefaultButton(false);
        addButton.setOnAction(event -> openCreateDialog());

        progressBar.setPrefWidth(180);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, progressLabel, progressBar, spacer, addButton);
        header.setAlignment(Pos.CENTER_LEFT);

        messageLabel.getStyleClass().add("muted");
        messageLabel.setWrapText(true);
        getChildren().addAll(header, messageLabel, list);
    }

    /** Se avisa cada vez que cambia la lista de rutas (para actualizar personajes y pestaña). */
    void setOnRoutesChanged(Consumer<List<Route>> listener) {
        this.onRoutesChanged = listener;
    }

    /**
     * Personajes disponibles para elegir como heroína y para las miniaturas.
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
                error -> Dialogs.error(this, error.getMessage()));
    }

    private void openCreateDialog() {
        new RouteDialog(this, null, selectableCharacters(null)).showAndWait().ifPresent(result ->
                runThenReload(() -> {
                    Route created = routeService.addRoute(vnId, result.characterId(), result.name(), result.notes());
                    if (result.status() != RouteStatus.PENDING) {
                        routeService.setStatus(created.id(), result.status());
                    }
                    return null;
                }));
    }

    private void openEditDialog(Route route) {
        new RouteDialog(this, route, selectableCharacters(route)).showAndWait().ifPresent(result ->
                runThenReload(() -> routeService.updateRoute(new Route(route.id(), vnId, result.characterId(),
                        result.name(), route.recommendedOrder(), result.status(), result.notes(),
                        route.completedAt()))));
    }

    private void confirmDelete(Route route) {
        if (Dialogs.confirm(this, "¿Borrar la ruta \"" + route.name() + "\"?",
                "Se perderán su estado y sus notas.")) {
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
        RouteProgress progress = routeService.progressOf(routes);
        progressLabel.setText(progress + " completadas");
        progressBar.setProgress(progress.fraction());

        messageLabel.setText(routes.isEmpty()
                ? "Aún no hay rutas. Márcalas desde la pestaña Personajes (\"+ Tiene ruta\") "
                  + "o añade una con el botón, también rutas sin heroína como la común o el true end."
                : "Orden recomendado de arriba abajo. Usa ▲ ▼ para cambiarlo.");

        list.getChildren().clear();
        for (int i = 0; i < routes.size(); i++) {
            list.getChildren().add(createRow(routes.get(i), i == 0, i == routes.size() - 1));
        }
    }

    private HBox createRow(Route route, boolean first, boolean last) {
        Label order = new Label(String.valueOf(route.recommendedOrder()));
        order.getStyleClass().add("route-order");

        // Miniatura de la heroína (si tiene y la conocemos)
        VnCharacter vc = route.characterId() == null ? null : charactersById.get(route.characterId());
        Region thumb;
        if (vc != null) {
            GameCharacter c = vc.character();
            Path file = c.imagePath() == null ? null : images.resolve(c.imagePath());
            thumb = new CoverView(file, contentFilter.isSensitive(c.imageSexual(), c.imageViolence()),
                    THUMB_WIDTH, THUMB_HEIGHT);
        } else {
            thumb = new Region();
            thumb.setMinSize(THUMB_WIDTH, THUMB_HEIGHT);
        }

        // Textos: nombre, heroína y notas
        Label name = new Label(route.name());
        name.getStyleClass().add("route-name");
        VBox texts = new VBox(2, name);
        String subtitle = route.characterId() == null ? "Sin heroína"
                : vc != null ? vc.character().name() : "Personaje " + route.characterId();
        if (!subtitle.equals(route.name())) { // no repetir si la ruta se llama como la heroína
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("muted");
            texts.getChildren().add(sub);
        }
        if (route.notes() != null) {
            Label notes = new Label(route.notes());
            notes.setWrapText(true);
            notes.getStyleClass().add("route-notes");
            texts.getChildren().add(notes);
        }
        HBox.setHgrow(texts, Priority.ALWAYS);

        // Estado: se cambia directamente desde el desplegable
        ComboBox<RouteStatus> status = new ComboBox<>();
        status.getItems().setAll(RouteStatus.values());
        status.setCellFactory(l -> new RouteDialog.StatusCell());
        status.setButtonCell(new RouteDialog.StatusCell());
        status.setValue(route.status());
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

        Button up = iconButton("▲", "Subir en el orden recomendado", first);
        up.setOnAction(e -> runThenReload(() -> {
            routeService.moveUp(route.id());
            return null;
        }));
        Button down = iconButton("▼", "Bajar en el orden recomendado", last);
        down.setOnAction(e -> runThenReload(() -> {
            routeService.moveDown(route.id());
            return null;
        }));
        Button edit = iconButton("✎", "Editar", false);
        edit.setOnAction(e -> openEditDialog(route));
        Button delete = iconButton("✕", "Borrar", false);
        delete.setOnAction(e -> confirmDelete(route));

        HBox row = new HBox(12, order, thumb, texts, status, up, down, edit, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("route-row", "status-" + route.status().name().toLowerCase().replace('_', '-'));
        return row;
    }

    private static Button iconButton(String text, String tooltip, boolean disabled) {
        Button button = new Button(text);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setDisable(disabled);
        return button;
    }
}

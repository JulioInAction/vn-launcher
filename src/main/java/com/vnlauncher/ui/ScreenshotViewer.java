package com.vnlauncher.ui;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Visor de capturas a pantalla completa (screenshot-viewer-dark.png): una capa encima de toda la ventana,
 * con fondo casi negro en los dos temas.
 * <ul>
 *   <li>arriba, el título de la VN, «Captura 4 de 12» y el botón de cerrar;</li>
 *   <li>en el centro, la captura grande (sin deformar) y flechas a los lados;</li>
 *   <li>abajo, las miniaturas de todas y la ayuda de teclado.</li>
 * </ul>
 * Teclas: ← y → para pasar (dan la vuelta) y Esc para cerrar; también cierra un clic fuera de la captura. Las sensibles se ven difuminadas con
 * un botón «Mostrar» hasta que se pulsa (y siguen destapadas al volver a la ficha).
 *
 * <p>Es un nodo más dentro del StackPane raíz de la ventana (main-view.fxml), por encima del resto:
 * no es otra ventana. Al cerrarlo se quita de ahí.
 */
final class ScreenshotViewer extends BorderPane {

    private static final double THUMB_WIDTH = 80;
    private static final double THUMB_HEIGHT = 45;
    private static final String CLOSE_ICON = "M6 6 L18 18 M18 6 L6 18";

    private final StackPane host;
    private final ScreenshotSet shots;
    private final IntConsumer onClose;
    private final Label counter = new Label();
    private final ScreenshotView image = new ScreenshotView(10);
    private final List<StackPane> thumbTiles = new ArrayList<>();
    private final ScrollPane thumbsScroll = new ScrollPane();
    private final Node previouslyFocused;
    private final Scene scene;
    /** Si se carga alguna miniatura con el visor abierto (se quita al cerrar). */
    private final IntConsumer thumbnailListener;
    private int current;

    /**
     * Abre el visor encima de la ventana.
     *
     * @param anyNode cualquier nodo de la ventana (para encontrar su StackPane raíz)
     * @param onClose recibe la captura que se estaba viendo al cerrar
     */
    static void show(Node anyNode, String title, ScreenshotSet shots, int start, IntConsumer onClose) {
        StackPane host = (StackPane) anyNode.getScene().getRoot();
        ScreenshotViewer viewer = new ScreenshotViewer(host, title, shots, onClose);
        host.getChildren().add(viewer);
        viewer.select(start);
        // El foco se pide cuando JavaFX termine con el clic que ha abierto el visor: si se pide
        // ahora, el resto de ese clic se lo devuelve a la ficha y las teclas no llegarían aquí
        Platform.runLater(viewer::requestFocus);
    }

    private ScreenshotViewer(StackPane host, String title, ScreenshotSet shots, IntConsumer onClose) {
        this.host = host;
        this.shots = shots;
        this.onClose = onClose;
        this.previouslyFocused = host.getScene().getFocusOwner();
        getStyleClass().add("screenshot-viewer");
        setFocusTraversable(true);

        // --- Arriba: título, «Captura N de M» y cerrar
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("viewer-title");
        counter.getStyleClass().add("viewer-counter");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = iconButton(CLOSE_ICON, "viewer-close", "Cerrar");
        close.setOnAction(event -> close());
        HBox top = new HBox(14, titleLabel, counter, spacer, close);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(20, 28, 16, 28));
        setTop(top);

        // --- Centro: la captura y las flechas
        Button prev = iconButton("M14 6 L8 12 L14 18", "viewer-arrow", "Captura anterior");
        Button next = iconButton("M10 6 L16 12 L10 18", "viewer-arrow", "Captura siguiente");
        prev.setOnAction(event -> step(-1));
        next.setOnAction(event -> step(1));
        prev.setVisible(shots.size() > 1);
        next.setVisible(shots.size() > 1);
        Button reveal = new Button("Mostrar");
        reveal.getStyleClass().add("viewer-reveal");
        reveal.setFocusTraversable(false);
        reveal.setOnAction(event -> {
            shots.reveal(current);
            select(current);
        });
        image.setCenterNode(reveal);
        setCenter(new ImageArea(image, prev, next));

        // --- Abajo: miniaturas (en un ScrollPane por si no caben) y ayuda
        HBox strip = new HBox(8);
        strip.setAlignment(Pos.CENTER);
        for (int i = 0; i < shots.size(); i++) {
            ScreenshotView view = new ScreenshotView(6);
            view.setMinSize(THUMB_WIDTH, THUMB_HEIGHT);
            view.setPrefSize(THUMB_WIDTH, THUMB_HEIGHT);
            view.setMaxSize(THUMB_WIDTH, THUMB_HEIGHT);
            view.setImage(shots.thumbnail(i));
            StackPane tile = new StackPane(view);
            tile.getStyleClass().add("viewer-thumb");
            int index = i;
            tile.setOnMouseClicked(event -> select(index));
            thumbTiles.add(tile);
            strip.getChildren().add(tile);
        }
        // Centradas si caben: la tira mide al menos lo que se ve del ScrollPane
        strip.minWidthProperty().bind(thumbsScroll.widthProperty());
        thumbsScroll.setContent(strip);
        thumbsScroll.getStyleClass().add("plain-scroll");
        thumbsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        thumbsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        thumbsScroll.setFocusTraversable(false);
        Label hint = new Label("← → para pasar · Esc o clic fuera para cerrar");
        hint.getStyleClass().add("viewer-hint");
        VBox bottom = new VBox(14, thumbsScroll, hint);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(18, 28, 20, 28));
        setBottom(bottom);
        thumbnailListener = index -> thumbView(index).setImage(shots.thumbnail(index));
        shots.addThumbnailListener(thumbnailListener);

        // --- Teclado: ver keyFilter. Va en la escena mientras el visor está abierto.
        scene = host.getScene();
        scene.addEventFilter(KeyEvent.ANY, keyFilter);
        // Clic en cualquier sitio que no sea la captura, un botón o una miniatura: se cierra. El clic
        // tampoco llega a la ficha de debajo (el visor lo consume).
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isOnContent((Node) event.getTarget())) {
                close();
            } else {
                requestFocus();
            }
            event.consume();
        });
    }

    /** true si el clic cayó en algo con función propia (la captura grande, un botón, una miniatura). */
    private boolean isOnContent(Node target) {
        for (Node node = target; node != null && node != this; node = node.getParent()) {
            if (node == image || node instanceof Button || thumbTiles.contains(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Teclas del visor. Es un "filtro" (addEventFilter) sobre la escena, no un manejador del visor:
     * una tecla va al nodo que tiene el foco (que puede seguir siendo un botón de la ficha, debajo
     * del visor, o ninguno) y solo pasa por la escena y los nodos que hay hasta él. Los filtros de la
     * escena se ejecutan antes que nadie, así que aquí llegan todas. Se consumen TODAS: con el visor
     * abierto, ninguna tecla debe llegar a la ficha (un Espacio pulsaría el botón que tuviera el foco).
     */
    private final EventHandler<KeyEvent> keyFilter = event -> {
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            switch (event.getCode()) {
                case LEFT -> step(-1);
                case RIGHT -> step(1);
                case ESCAPE -> close();
                default -> { }
            }
        }
        event.consume();
    };

    /** Pasa a la anterior (-1) o a la siguiente (+1), dando la vuelta en los extremos. */
    private void step(int delta) {
        select(Math.floorMod(current + delta, shots.size()));
    }

    private void select(int index) {
        current = index;
        counter.setText("Captura " + (index + 1) + " de " + shots.size());

        image.setSensitive(shots.isSensitive(index), shots.isRevealed(index));
        image.setImage(shots.thumbnail(index)); // mientras llega la grande
        shots.largeImage(index, 0, loaded -> {
            if (current == index) {
                image.setImage(loaded);
            }
        });
        // Precarga de la anterior y la siguiente: al pasar, ya están en memoria
        if (shots.size() > 1) {
            shots.largeImage(Math.floorMod(index - 1, shots.size()), 0, loaded -> { });
            shots.largeImage((index + 1) % shots.size(), 0, loaded -> { });
        }

        for (int i = 0; i < thumbTiles.size(); i++) {
            StackPane tile = thumbTiles.get(i);
            tile.getStyleClass().remove("current");
            if (i == index) {
                tile.getStyleClass().add("current");
            }
            ScreenshotView view = thumbView(i);
            view.setOpacity(i == index ? 1 : 0.5);
            view.setSensitive(shots.isSensitive(i), shots.isRevealed(i));
        }
        scrollThumbIntoView(index);
    }

    /** Si las miniaturas no caben, desplaza la tira para que la actual quede en el centro. */
    private void scrollThumbIntoView(int index) {
        double content = thumbsScroll.getContent().getLayoutBounds().getWidth();
        double visible = thumbsScroll.getViewportBounds().getWidth();
        if (content <= visible || visible <= 0) {
            return;
        }
        StackPane tile = thumbTiles.get(index);
        double center = tile.getBoundsInParent().getCenterX();
        double target = (center - visible / 2) / (content - visible);
        thumbsScroll.setHvalue(Math.clamp(target, 0, 1));
    }

    private ScreenshotView thumbView(int index) {
        return (ScreenshotView) thumbTiles.get(index).getChildren().getFirst();
    }

    private void close() {
        scene.removeEventFilter(KeyEvent.ANY, keyFilter);
        host.getChildren().remove(this);
        shots.removeThumbnailListener(thumbnailListener);
        onClose.accept(current);
        if (previouslyFocused != null) {
            previouslyFocused.requestFocus();
        }
    }

    private static Button iconButton(String path, String styleClass, String description) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("viewer-icon");
        Button button = new Button(null, new Group(icon));
        button.getStyleClass().add(styleClass);
        button.setAccessibleText(description);
        button.setFocusTraversable(false); // el teclado lo maneja el visor (ver el filtro de teclas)
        return button;
    }

    /**
     * Zona central: la captura lo más grande posible sin deformarse (ajustada a su proporción,
     * 16:9 mientras no se sabe) entre las dos flechas, que van centradas en vertical.
     */
    private static final class ImageArea extends Region {

        private static final double ARROW = 56;
        private static final double SIDE = 28;
        /** Hueco entre la flecha y la imagen. */
        private static final double GAP = 16;

        private final ScreenshotView image;
        private final Button prev;
        private final Button next;

        ImageArea(ScreenshotView image, Button prev, Button next) {
            this.image = image;
            this.prev = prev;
            this.next = next;
            getChildren().addAll(image, prev, next);
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            double y = Math.round((h - ARROW) / 2);
            prev.resizeRelocate(SIDE, y, ARROW, ARROW);
            next.resizeRelocate(w - SIDE - ARROW, y, ARROW, ARROW);

            double margin = SIDE + ARROW + GAP;
            double availW = Math.max(0, w - 2 * margin);
            Image img = image.getImage();
            double ratio = img != null && img.getWidth() > 0 ? img.getWidth() / img.getHeight() : 16.0 / 9;
            double iw = Math.min(availW, h * ratio);
            double ih = iw / ratio;
            iw = Math.round(iw);
            ih = Math.round(ih);
            image.resizeRelocate(Math.round((w - iw) / 2), Math.round((h - ih) / 2), iw, ih);
        }
    }
}

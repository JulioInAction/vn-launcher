package com.vnlauncher.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Galería de capturas de la cabecera de la ficha (vn-detail-*.png):
 * <ul>
 *   <li>arriba, la captura principal en 16:9 con un contador «1 / 12» y flechas a los lados (al
 *       llegar al final vuelve a la primera);</li>
 *   <li>debajo, una fila de 6 miniaturas: la elegida con un anillo rosa y las demás algo apagadas.
 *       Si hay más de 6, la fila se desplaza para que la elegida siempre se vea.</li>
 * </ul>
 *
 * <p>Es una Region con su propio {@link #layoutChildren()}: el alto depende del ancho (16:9), y eso
 * es más fácil de calcular aquí que combinando HBox y VBox. Por eso declara
 * {@code getContentBias() = HORIZONTAL}: "dime el ancho y te digo el alto".
 *
 * <p>Las miniaturas se descargan al abrir la ficha; la imagen grande, solo al mostrarla como
 * principal (mientras llega se ve la miniatura ampliada).
 */
final class ScreenshotGallery extends Region {

    static final int VISIBLE_THUMBS = 6;
    private static final double MAIN_RADIUS = 12;
    private static final double THUMB_RADIUS = 8;
    private static final double THUMB_GAP = 8;
    /** Separación entre la captura principal y las miniaturas. */
    private static final double ROW_GAP = 10;
    /** El anillo de la elegida va 2 px por fuera y mide 2 px: ocupa 4 px alrededor de la miniatura. */
    private static final double RING = 4;
    private static final double ARROW_SIZE = 48;
    private static final double ARROW_INSET = 12;
    private static final double COUNTER_INSET = 10;
    /** Ancho al que se carga la imagen grande: el máximo de la galería. */
    static final double MAX_WIDTH = 680;

    private static final String CHEVRON_LEFT = "M14 6 L8 12 L14 18";
    private static final String CHEVRON_RIGHT = "M10 6 L16 12 L10 18";

    private final ScreenshotSet shots;
    private final ScreenshotView main = new ScreenshotView(MAIN_RADIUS).withFrame("shot-frame").withBadge();
    private final Label counter = new Label();
    private final Button prev = arrow(CHEVRON_LEFT, "Captura anterior");
    private final Button next = arrow(CHEVRON_RIGHT, "Captura siguiente");
    /** Ventana por la que se ven las miniaturas (recorta las que no caben) y la tira que se mueve. */
    private final Pane thumbsViewport = new Pane();
    private final Pane thumbsStrip = new Pane();
    private final List<ScreenshotView> thumbs = new ArrayList<>();
    private final Region ring = new Region();
    /** Primera miniatura visible (con decimales mientras se anima el desplazamiento). */
    private final DoubleProperty scroll = new SimpleDoubleProperty(0);
    private Timeline scrollAnimation;
    private int selected;
    private IntConsumer onOpen = index -> { };

    ScreenshotGallery(ScreenshotSet shots) {
        this.shots = shots;
        getStyleClass().add("screenshot-gallery");

        prev.setOnAction(event -> select(Math.floorMod(selected - 1, shots.size())));
        next.setOnAction(event -> select((selected + 1) % shots.size()));
        boolean several = shots.size() > 1;
        prev.setVisible(several);
        next.setVisible(several);
        counter.getStyleClass().add("gallery-counter");
        counter.setMouseTransparent(true);

        for (int i = 0; i < shots.size(); i++) {
            ScreenshotView thumb = new ScreenshotView(THUMB_RADIUS);
            thumb.setSensitive(shots.isSensitive(i), shots.isRevealed(i));
            thumb.setImage(shots.thumbnail(i));
            thumb.setCursor(Cursor.HAND);
            int index = i;
            thumb.setOnMouseClicked(event -> select(index));
            thumbs.add(thumb);
        }
        ring.getStyleClass().add("shot-ring");
        ring.setMouseTransparent(true);
        thumbsStrip.getChildren().addAll(thumbs);
        thumbsStrip.getChildren().add(ring);
        thumbsViewport.getChildren().add(thumbsStrip);
        // Un Pane, en su propio layout, devuelve a sus hijos a su tamaño preferido. Estos los coloca
        // layoutChildren() de la galería, así que se marcan como "no gestionados" (managed = false)
        // para que el Pane no los toque.
        thumbsStrip.setManaged(false);
        thumbsStrip.getChildren().forEach(node -> node.setManaged(false));
        Rectangle viewportClip = new Rectangle();
        viewportClip.widthProperty().bind(thumbsViewport.widthProperty());
        viewportClip.heightProperty().bind(thumbsViewport.heightProperty());
        thumbsViewport.setClip(viewportClip);
        // Con una sola captura no hace falta fila de miniaturas
        thumbsViewport.setVisible(several);

        main.setOnMouseClicked(event -> onOpen.accept(selected));
        getChildren().addAll(main, counter, prev, next, thumbsViewport);

        scroll.addListener(observable -> requestLayout());
        shots.addThumbnailListener(this::thumbnailLoaded);
        shots.loadThumbnails();
        select(0);
    }

    /** Qué hacer al pulsar la captura principal (abrir el visor con esa captura). */
    void setOnOpen(IntConsumer onOpen) {
        this.onOpen = onOpen;
        main.setCursor(Cursor.HAND);
    }

    int selectedIndex() {
        return selected;
    }

    /** Pasa a mostrar la captura {@code index} como principal. */
    void select(int index) {
        selected = index;
        counter.setText((index + 1) + " / " + shots.size());
        for (int i = 0; i < thumbs.size(); i++) {
            thumbs.get(i).setOpacity(i == index ? 1 : 0.7);
            thumbs.get(i).setSensitive(shots.isSensitive(i), shots.isRevealed(i));
        }
        main.setSensitive(shots.isSensitive(index), shots.isRevealed(index));
        // Mientras llega la grande, la miniatura ampliada (mejor que un hueco vacío)
        main.setImage(shots.thumbnail(index));
        shots.largeImage(index, MAX_WIDTH, image -> {
            if (selected == index) {
                main.setImage(image);
            }
        });
        scrollToSelected();
        requestLayout();
    }

    /** Desplaza la fila lo justo para que la elegida se vea (animado, 160 ms). */
    private void scrollToSelected() {
        double first = scroll.get();
        double target = first;
        if (selected < first) {
            target = selected;
        } else if (selected > first + VISIBLE_THUMBS - 1) {
            target = selected - (VISIBLE_THUMBS - 1);
        }
        target = Math.max(0, Math.min(target, Math.max(0, thumbs.size() - VISIBLE_THUMBS)));
        if (target == first) {
            return;
        }
        if (scrollAnimation != null) {
            scrollAnimation.stop();
        }
        scrollAnimation = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(scroll, target, Interpolator.EASE_OUT)));
        scrollAnimation.play();
    }

    private void thumbnailLoaded(int index) {
        thumbs.get(index).setImage(shots.thumbnail(index));
        if (index == selected && main.getImage() == null) {
            main.setImage(shots.thumbnail(index));
        }
    }

    // ------------------------------------------------------------------ medidas

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefWidth(double height) {
        return MAX_WIDTH;
    }

    @Override
    protected double computeMinWidth(double height) {
        return 0; // el ancho lo decide quien la contiene (VnDetailController)
    }

    @Override
    protected double computePrefHeight(double width) {
        double w = width > 0 ? width : MAX_WIDTH;
        return mainHeight(w) + (thumbs.size() > 1 ? ROW_GAP + thumbHeight(w) + RING : 0);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    private static double mainHeight(double width) {
        return Math.round(width * 9 / 16);
    }

    private static double thumbWidth(double width) {
        return (width - (VISIBLE_THUMBS - 1) * THUMB_GAP) / VISIBLE_THUMBS;
    }

    private static double thumbHeight(double width) {
        return Math.round(thumbWidth(width) * 9 / 16);
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double mainH = mainHeight(w);
        main.resizeRelocate(0, 0, w, mainH);

        prev.resizeRelocate(ARROW_INSET, Math.round((mainH - ARROW_SIZE) / 2), ARROW_SIZE, ARROW_SIZE);
        next.resizeRelocate(Math.round(w - ARROW_INSET - ARROW_SIZE), Math.round((mainH - ARROW_SIZE) / 2),
                ARROW_SIZE, ARROW_SIZE);
        counter.autosize();
        counter.relocate(Math.round(w - COUNTER_INSET - counter.getWidth()),
                Math.round(mainH - COUNTER_INSET - counter.getHeight()));

        // Fila de miniaturas. La ventana sobresale 4 px por cada lado para que el anillo de la
        // primera y la última no se corte.
        double tw = thumbWidth(w);
        double th = thumbHeight(w);
        thumbsViewport.resizeRelocate(-RING, mainH + ROW_GAP - RING, w + 2 * RING, th + 2 * RING);
        double step = tw + THUMB_GAP;
        for (int i = 0; i < thumbs.size(); i++) {
            // Bordes redondeados a píxeles enteros: todas las miniaturas iguales y nítidas
            double x = Math.round(i * step);
            double width = Math.round(i * step + tw) - x;
            thumbs.get(i).resizeRelocate(RING + x, RING, width, th);
        }
        if (!thumbs.isEmpty()) {
            ScreenshotView current = thumbs.get(selected);
            ring.resizeRelocate(current.getLayoutX() - RING, 0, current.getWidth() + 2 * RING, th + 2 * RING);
        }
        thumbsStrip.resize(RING * 2 + thumbs.size() * step, th + 2 * RING);
        thumbsStrip.setLayoutX(-Math.round(scroll.get() * step));
    }

    private static Button arrow(String chevron, String description) {
        SVGPath path = new SVGPath();
        path.setContent(chevron);
        path.getStyleClass().add("gallery-arrow-icon");
        Button button = new Button(null, new Group(path));
        button.getStyleClass().add("gallery-arrow");
        button.setAccessibleText(description);
        return button;
    }
}

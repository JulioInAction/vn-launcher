package com.vnlauncher.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * Control de "Cambiar portada" bajo la portada de la ficha (docs/design/mockups/cover-states-dark.png).
 *
 * <ul>
 *   <li><b>En reposo:</b> una franja rosa de 8 px (radio 4) con una lengüeta central de 40 × 15 px
 *       que lleva una flecha hacia abajo.</li>
 *   <li><b>Ratón encima de la franja o foco del teclado:</b> crece hasta un
 *       botón de 44 px (radio 10) con «Cambiar portada»; con el foco lleva además un contorno.</li>
 * </ul>
 *
 * <p>La animación es una {@link Timeline} de 180 ms que mueve varias propiedades a la vez: el alto
 * y el radio de la franja (frenando al final), el texto (aparece desde los 55 ms subiendo 6 px) y
 * la flecha (gira 180° y se desvanece). Para cerrar se reproduce la misma Timeline hacia atrás.
 *
 * <p>El texto vive en una capa con un clip redondeado que sigue a la franja: mientras crece, solo
 * se ve la parte del texto que ya está "dentro" del botón.
 *
 * <p>El control mide siempre 44 px de alto (y la ficha deja 6 px encima): así reserva los 50 px
 * bajo la portada y no mueve nada al abrirse.
 */
final class CoverChanger extends Pane {

    static final double GAP = 6;
    static final double REST_HEIGHT = 8;
    static final double OPEN_HEIGHT = 44;
    private static final double REST_RADIUS = 4;
    private static final double OPEN_RADIUS = 10;
    private static final double TAB_WIDTH = 40;
    private static final double TAB_HEIGHT = 15;
    /** Separación del contorno de foco alrededor del botón. */
    private static final double RING_GAP = 4;

    private static final Duration GROW = Duration.millis(180);
    private static final Duration TEXT_DELAY = Duration.millis(55);
    private static final Duration CLOSE_DELAY = Duration.millis(150);

    /** Flecha hacia abajo en una caja de 14 × 14. */
    private static final String ARROW = "M3.5 5.5 L7 9 L10.5 5.5";
    /** Icono de imagen (marco, sol y montaña) en una caja de 20 × 20. */
    private static final String IMAGE_ICON = "M4 4.5 H16 A1.5 1.5 0 0 1 17.5 6 V14 A1.5 1.5 0 0 1 16 15.5 H4 "
            + "A1.5 1.5 0 0 1 2.5 14 V6 A1.5 1.5 0 0 1 4 4.5 Z M2.5 12.5 L7 9 L10.5 12 L13 10 L17.5 13.5 "
            + "M12.5 7.2 A1.2 1.2 0 1 0 12.51 7.2";

    /** Valores que anima la Timeline; las formas están enlazadas a ellos. */
    private final DoubleProperty barHeight = new SimpleDoubleProperty(REST_HEIGHT);
    private final DoubleProperty radius = new SimpleDoubleProperty(REST_RADIUS);

    private final Rectangle bar = new Rectangle();
    private final Rectangle tab = new Rectangle(TAB_WIDTH, REST_HEIGHT + TAB_HEIGHT);
    private final Group arrow;
    private final HBox content;
    private final Pane contentLayer = new Pane();
    private final Rectangle focusRing = new Rectangle();
    private final Timeline timeline;
    private final PauseTransition closeDelay = new PauseTransition(CLOSE_DELAY);

    /**
     * @param width  ancho, el de la portada
     * @param onOpen qué hacer al pulsarlo (abrir la galería)
     */
    CoverChanger(double width, Runnable onOpen) {
        getStyleClass().add("cover-changer");
        setPrefSize(width, OPEN_HEIGHT);
        setMinSize(width, OPEN_HEIGHT);
        setMaxSize(width, OPEN_HEIGHT);
        // Solo las formas visibles reciben el ratón, no el rectángulo entero de 44 px: en reposo,
        // pasar por debajo de la franja (donde no hay nada) no debe abrirlo.
        setPickOnBounds(false);

        // --- Franja / botón: un Rectangle cuyo alto y esquinas se enlazan a los valores animados
        bar.getStyleClass().add("cover-changer-bar");
        bar.setWidth(width);
        bar.heightProperty().bind(barHeight);
        bar.arcWidthProperty().bind(radius.multiply(2));   // en JavaFX "arc" es el DIÁMETRO
        bar.arcHeightProperty().bind(radius.multiply(2));

        // --- Lengüeta: empieza dentro de la franja (sus esquinas de arriba quedan tapadas por ella,
        //     que es del mismo color) y cuelga 15 px por debajo
        tab.getStyleClass().add("cover-changer-bar");
        tab.setArcWidth(10);
        tab.setArcHeight(10);
        tab.relocate(Math.round((width - TAB_WIDTH) / 2), 0);

        SVGPath arrowPath = new SVGPath();
        arrowPath.setContent(ARROW);
        arrowPath.getStyleClass().add("cover-changer-arrow");
        // El Rectangle invisible fija la caja de 14 × 14: así la flecha gira sobre su centro
        Rectangle arrowBox = new Rectangle(14, 14);
        arrowBox.getStyleClass().add("cover-changer-arrow-box");
        arrow = new Group(arrowBox, arrowPath);
        arrow.setMouseTransparent(true);
        arrow.relocate(Math.round((width - 14) / 2), Math.round(REST_HEIGHT + (TAB_HEIGHT - 14) / 2));

        // --- Contenido del botón abierto: icono + texto, centrado en los 44 px
        SVGPath icon = new SVGPath();
        icon.setContent(IMAGE_ICON);
        icon.getStyleClass().add("cover-changer-icon");
        Label text = new Label("Cambiar portada");
        text.getStyleClass().add("cover-changer-text");
        content = new HBox(10, new Group(icon), text);
        content.setAlignment(Pos.CENTER);
        content.setPrefSize(width, OPEN_HEIGHT);
        content.setOpacity(0);
        content.setTranslateY(6);
        contentLayer.getChildren().add(content);
        contentLayer.setMouseTransparent(true);
        // Clip redondeado que sigue a la franja: el texto solo se ve dentro del botón
        Rectangle clip = new Rectangle(width, REST_HEIGHT);
        clip.heightProperty().bind(barHeight);
        clip.arcWidthProperty().bind(radius.multiply(2));
        clip.arcHeightProperty().bind(radius.multiply(2));
        contentLayer.setClip(clip);

        // --- Contorno del foco del teclado, 4 px por fuera del botón
        focusRing.getStyleClass().add("cover-changer-focus");
        focusRing.setMouseTransparent(true);
        focusRing.setWidth(width + 2 * RING_GAP);
        focusRing.heightProperty().bind(barHeight.add(2 * RING_GAP));
        focusRing.arcWidthProperty().bind(radius.add(RING_GAP).multiply(2));
        focusRing.arcHeightProperty().bind(radius.add(RING_GAP).multiply(2));
        focusRing.relocate(-RING_GAP, -RING_GAP);
        focusRing.visibleProperty().bind(focusVisibleProperty());

        getChildren().addAll(tab, bar, arrow, contentLayer, focusRing);

        // --- Animación: 0 ms = en reposo, 180 ms = abierto. Para cerrar, la misma hacia atrás.
        Interpolator easeOut = Interpolator.EASE_OUT; // rápido al principio, frena al final
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(barHeight, REST_HEIGHT),
                        new KeyValue(radius, REST_RADIUS),
                        new KeyValue(content.opacityProperty(), 0),
                        new KeyValue(content.translateYProperty(), 6),
                        new KeyValue(arrow.rotateProperty(), 0),
                        new KeyValue(arrow.opacityProperty(), 1)),
                // Hasta los 55 ms el texto no se mueve: aparece cuando el botón ya ha crecido algo
                new KeyFrame(TEXT_DELAY,
                        new KeyValue(content.opacityProperty(), 0),
                        new KeyValue(content.translateYProperty(), 6)),
                new KeyFrame(GROW,
                        new KeyValue(barHeight, OPEN_HEIGHT, easeOut),
                        new KeyValue(radius, OPEN_RADIUS, easeOut),
                        new KeyValue(content.opacityProperty(), 1, easeOut),
                        new KeyValue(content.translateYProperty(), 0, easeOut),
                        new KeyValue(arrow.rotateProperty(), 180, easeOut),
                        new KeyValue(arrow.opacityProperty(), 0, easeOut)));
        closeDelay.setOnFinished(event -> play(false));

        // --- Cuándo abrir: ratón sobre la franja (o la lengüeta) o foco del teclado. La portada no
        //     lo abre: al bajar de ella a la franja, el hueco de 6 px lo cerraba y abría a saltos.
        InvalidationListener update = observable -> {
            if (isHover() || isFocusVisible()) {
                closeDelay.stop();
                play(true);
            } else {
                closeDelay.playFromStart(); // espera 150 ms antes de cerrarse
            }
        };
        hoverProperty().addListener(update);
        focusVisibleProperty().addListener(update);

        // --- Es un botón: clic, Enter o espacio
        setFocusTraversable(true);
        setCursor(Cursor.HAND);
        setAccessibleRole(AccessibleRole.BUTTON);
        setAccessibleText("Cambiar portada");
        setOnMouseClicked(event -> onOpen.run());
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                event.consume();
                onOpen.run();
            }
        });
    }

    /**
     * Reproduce la Timeline hacia delante (abrir) o hacia atrás (cerrar) desde donde esté: si se
     * cierra a medio abrir, vuelve desde ese punto, sin saltos.
     */
    private void play(boolean open) {
        timeline.setRate(open ? 1 : -1);
        // Una Animation parada al final se queda en el último instante, pero por si acaso:
        // si el control está abierto y hay que cerrarlo, se coloca al final antes de ir hacia atrás.
        if (!open && timeline.getCurrentTime().equals(Duration.ZERO) && barHeight.get() > REST_HEIGHT) {
            timeline.jumpTo(GROW);
        }
        timeline.play();
    }

    /** Para los tests y las capturas: el alto actual de la franja. */
    double barHeight() {
        return barHeight.get();
    }
}

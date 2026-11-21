package com.vnlauncher.ui;

import com.vnlauncher.util.BoxBlur;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * Una captura de pantalla de cualquier tamaño: la imagen entera (sin deformar ni recortar) centrada
 * en el hueco, con esquinas redondeadas, difuminada si es sensible y no se ha destapado.
 *
 * <p>A diferencia de {@link CoverView}, que tiene un tamaño fijo, esta se adapta a lo que le dé su
 * contenedor (la galería y el visor cambian de tamaño con la ventana): en {@link #layoutChildren()}
 * se recalcula dónde va la imagen. Si la proporción de la imagen no es la del hueco, quedan franjas
 * del color de fondo.
 *
 * <p><b>Cómo se difumina.</b> Un GaussianBlur sobre la imagen grande se recalcula en cada
 * fotograma, y sin tarjeta gráfica (JavaFX dibujando por software: máquinas virtuales, escritorio
 * remoto) eso tarda segundos con una captura a pantalla completa. Así que se difumina UNA vez una
 * copia diminuta (64 px de ancho, con {@link BoxBlur}) y se muestra ampliada: se ve igual de
 * borrosa y dibujarla no cuesta nada.
 */
final class ScreenshotView extends Region {

    /** Ancho de la copia difuminada y radio del difuminado sobre ese ancho (en píxeles de la copia). */
    private static final double BLURRED_WIDTH = 64;
    private static final int BLUR_RADIUS = 3;

    private final ImageView imageView = new ImageView();
    private final Rectangle clip = new Rectangle();
    /** Contorno dibujado ENCIMA de la imagen (el borde propio de una Region queda debajo). */
    private Region frame;
    /** Etiqueta «Sensible»; null si este tamaño no la lleva (miniaturas). */
    private Label badge;
    /** Algo centrado encima de la imagen (el botón «Mostrar» del visor); null = nada. */
    private Node centerNode;
    /** La imagen de verdad, y su copia pequeña difuminada (se crea la primera vez que hace falta). */
    private Image source;
    private Image blurred;
    private boolean sensitive;
    private boolean revealed;

    /** @param radius radio de las esquinas */
    ScreenshotView(double radius) {
        getStyleClass().add("shot");
        // La proporción la calcula layoutChildren con la imagen original: así la copia difuminada
        // (que es un poco distinta) ocupa exactamente el mismo sitio
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        clip.setArcWidth(radius * 2); // "arc" es el diámetro
        clip.setArcHeight(radius * 2);
        setClip(clip);
        getChildren().add(imageView);
    }

    /** Añade el contorno de encima (clase CSS {@code styleClass}, con el mismo radio). */
    ScreenshotView withFrame(String styleClass) {
        frame = new Region();
        frame.getStyleClass().add(styleClass);
        frame.setMouseTransparent(true);
        getChildren().add(frame);
        return this;
    }

    /** Añade la etiqueta «Sensible» arriba a la derecha (solo se ve si está difuminada). */
    ScreenshotView withBadge() {
        badge = new Label("Sensible");
        badge.getStyleClass().add("sensitive-badge");
        badge.setMouseTransparent(true);
        getChildren().add(badge);
        update();
        return this;
    }

    /** Pone un nodo centrado encima de la imagen (solo se ve si está difuminada). */
    void setCenterNode(Node node) {
        if (centerNode != null) {
            getChildren().remove(centerNode);
        }
        centerNode = node;
        if (node != null) {
            getChildren().add(node);
        }
        update();
    }

    void setImage(Image image) {
        if (image == source) {
            return;
        }
        source = image;
        blurred = null;
        if (image != null && image.getProgress() < 1) {
            // Se está cargando en segundo plano: hasta que termine no se sabe su tamaño ni se puede
            // difuminar, así que al terminar se vuelve a pintar
            image.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1 && source == image) {
                    update();
                }
            });
        }
        update();
    }

    /** La imagen original (no la copia difuminada). */
    Image getImage() {
        return source;
    }

    /** Sensible (según el ajuste «Difuminar imágenes») y si el usuario ya la ha destapado. */
    void setSensitive(boolean sensitive, boolean revealed) {
        this.sensitive = sensitive;
        this.revealed = revealed;
        update();
    }

    /** true si ahora mismo se ve difuminada. */
    boolean isHiddenBySensitivity() {
        return sensitive && !revealed;
    }

    /** Qué se ve: la imagen, su copia difuminada o nada (nunca la imagen sin difuminar si toca ocultarla). */
    private void update() {
        boolean hide = isHiddenBySensitivity();
        if (badge != null) {
            badge.setVisible(hide);
        }
        if (centerNode != null) {
            centerNode.setVisible(hide);
        }
        boolean ready = source != null && source.getProgress() >= 1 && !source.isError();
        if (!hide) {
            imageView.setImage(source);
        } else if (ready) {
            if (blurred == null) {
                blurred = blurredCopy(source);
            }
            imageView.setImage(blurred);
        } else {
            imageView.setImage(null);
        }
        requestLayout();
    }

    /**
     * Copia de 64 px de ancho difuminada: se "fotografía" (snapshot) un ImageView pequeño con la
     * imagen, se leen sus píxeles y se difuminan con {@link BoxBlur}, que mantiene opacos los bordes.
     */
    private static Image blurredCopy(Image image) {
        int w = (int) BLURRED_WIDTH;
        int h = (int) Math.max(1, Math.round(BLURRED_WIDTH * image.getHeight() / image.getWidth()));
        ImageView small = new ImageView(image);
        small.setFitWidth(w);
        small.setFitHeight(h);
        small.setSmooth(true);
        WritableImage copy = small.snapshot(null, new WritableImage(w, h));
        int[] pixels = new int[w * h];
        copy.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BoxBlur.blur(pixels, w, h, BLUR_RADIUS, 3);
        copy.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return copy;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        clip.setWidth(w);
        clip.setHeight(h);

        if (source != null && source.getWidth() > 0 && source.getHeight() > 0) {
            // Escala para que quepa entera; posición redondeada a píxeles enteros (sin borrosidad)
            double scale = Math.min(w / source.getWidth(), h / source.getHeight());
            double iw = Math.round(source.getWidth() * scale);
            double ih = Math.round(source.getHeight() * scale);
            imageView.setFitWidth(iw);
            imageView.setFitHeight(ih);
            imageView.relocate(Math.round((w - iw) / 2), Math.round((h - ih) / 2));
        }
        if (frame != null) {
            frame.resizeRelocate(0, 0, w, h);
        }
        if (badge != null) {
            badge.autosize();
            badge.relocate(Math.round(w - badge.getWidth()), 0);
        }
        if (centerNode != null) {
            centerNode.autosize();
            Node n = centerNode;
            n.relocate(Math.round((w - n.getLayoutBounds().getWidth()) / 2),
                    Math.round((h - n.getLayoutBounds().getHeight()) / 2));
        }
    }
}

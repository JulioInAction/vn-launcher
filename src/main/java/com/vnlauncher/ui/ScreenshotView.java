package com.vnlauncher.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
 */
final class ScreenshotView extends Region {

    private static final double BLUR_RADIUS = 63; // el máximo de GaussianBlur, como en CoverView

    private final ImageView imageView = new ImageView();
    private final Rectangle clip = new Rectangle();
    /** Contorno dibujado ENCIMA de la imagen (el borde propio de una Region queda debajo). */
    private Region frame;
    /** Etiqueta «Sensible»; null si este tamaño no la lleva (miniaturas). */
    private Label badge;
    /** Algo centrado encima de la imagen (el botón «Mostrar» del visor); null = nada. */
    private Node centerNode;
    private boolean sensitive;
    private boolean revealed;

    /** @param radius radio de las esquinas */
    ScreenshotView(double radius) {
        getStyleClass().add("shot");
        imageView.setPreserveRatio(true);
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
        updateBlur();
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
        updateBlur();
    }

    void setImage(Image image) {
        imageView.setImage(image);
        if (image != null && image.getWidth() == 0) {
            // Se está cargando en segundo plano: su tamaño se sabe al terminar, y entonces hay que
            // volver a colocarla
            image.widthProperty().addListener(observable -> requestLayout());
        }
        requestLayout();
    }

    Image getImage() {
        return imageView.getImage();
    }

    /** Sensible (según el ajuste «Difuminar imágenes») y si el usuario ya la ha destapado. */
    void setSensitive(boolean sensitive, boolean revealed) {
        this.sensitive = sensitive;
        this.revealed = revealed;
        updateBlur();
    }

    /** true si ahora mismo se ve difuminada. */
    boolean isHiddenBySensitivity() {
        return sensitive && !revealed;
    }

    private void updateBlur() {
        boolean hide = isHiddenBySensitivity();
        imageView.setEffect(hide ? new GaussianBlur(BLUR_RADIUS) : null);
        if (badge != null) {
            badge.setVisible(hide);
        }
        if (centerNode != null) {
            centerNode.setVisible(hide);
        }
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        clip.setWidth(w);
        clip.setHeight(h);

        Image image = imageView.getImage();
        if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
            // Escala para que quepa entera; posición redondeada a píxeles enteros (sin borrosidad)
            double scale = Math.min(w / image.getWidth(), h / image.getHeight());
            double iw = Math.round(image.getWidth() * scale);
            double ih = Math.round(image.getHeight() * scale);
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

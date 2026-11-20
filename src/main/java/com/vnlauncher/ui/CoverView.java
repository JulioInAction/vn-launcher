package com.vnlauncher.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Una imagen (portada o personaje) de tamaño fijo que:
 * <ul>
 *   <li>se carga desde disco en segundo plano (no congela la interfaz con muchas portadas),</li>
 *   <li>muestra "Sin imagen" si no hay fichero,</li>
 *   <li>si es sensible, se difumina y lleva una etiqueta, hasta que se llame a setRevealed(true).</li>
 * </ul>
 * StackPane apila sus hijos uno encima de otro: imagen abajo, etiqueta "Sensible" encima.
 */
public class CoverView extends StackPane {

    /** Radio del desenfoque. 63 es el máximo que admite GaussianBlur. */
    private static final double BLUR_RADIUS = 63;

    private final ImageView imageView = new ImageView();
    private final Label sensitiveBadge = new Label("Sensible");
    private final boolean sensitive;

    /**
     * @param file      imagen en disco, o null si no hay
     * @param sensitive si debe mostrarse difuminada por defecto
     */
    public CoverView(Path file, boolean sensitive, double width, double height) {
        this.sensitive = sensitive;
        getStyleClass().add("cover");
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        // Recorte: sin él, el desenfoque "se derrama" por fuera de la portada como un halo.
        setClip(new Rectangle(width, height));

        if (file != null && Files.exists(file)) {
            // Parámetros: url, ancho, alto, conservar proporción, suavizado, cargar en segundo plano.
            // Pedir ya el tamaño final ahorra memoria: no guardamos la imagen original entera.
            imageView.setImage(new Image(file.toUri().toString(), width, height, true, true, true));
            getChildren().add(imageView);
        } else {
            Label placeholder = new Label("Sin imagen");
            placeholder.getStyleClass().add("muted");
            getChildren().add(placeholder);
        }

        sensitiveBadge.getStyleClass().add("sensitive-badge");
        StackPane.setAlignment(sensitiveBadge, Pos.TOP_RIGHT);
        getChildren().add(sensitiveBadge);
        setRevealed(false);
    }

    /** Mostrar (true) u ocultar (false) la imagen si es sensible. Si no lo es, no cambia nada. */
    public void setRevealed(boolean revealed) {
        boolean hide = sensitive && !revealed;
        imageView.setEffect(hide ? new GaussianBlur(BLUR_RADIUS) : null);
        sensitiveBadge.setVisible(hide);
    }

    public boolean isSensitive() {
        return sensitive;
    }
}

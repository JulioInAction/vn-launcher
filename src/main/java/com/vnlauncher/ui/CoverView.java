package com.vnlauncher.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Una imagen (portada o personaje) de tamaño fijo que:
 * <ul>
 *   <li>se carga desde disco en segundo plano (no congela la interfaz con muchas portadas),</li>
 *   <li>si no hay fichero, muestra las iniciales del nombre sobre un color propio de ese nombre
 *       (o "Sin imagen" si no se da nombre),</li>
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

    public CoverView(Path file, boolean sensitive, double width, double height) {
        this(file, sensitive, width, height, null);
    }

    /**
     * @param file      imagen en disco, o null si no hay
     * @param sensitive si debe mostrarse difuminada por defecto
     * @param name      nombre para las iniciales cuando no hay imagen (null = "Sin imagen")
     */
    public CoverView(Path file, boolean sensitive, double width, double height, String name) {
        this.sensitive = sensitive;
        getStyleClass().add("cover");
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        // Recorte con esquinas redondeadas: sin él, el desenfoque "se derrama" por fuera como un halo.
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        setClip(clip);

        if (file != null && Files.exists(file)) {
            // Parámetros: url, ancho, alto, conservar proporción, suavizado, cargar en segundo plano.
            // Pedir ya el tamaño final ahorra memoria: no guardamos la imagen original entera.
            imageView.setImage(new Image(file.toUri().toString(), width, height, true, true, true));
            getChildren().add(imageView);
        } else if (name != null) {
            setBackground(new Background(new BackgroundFill(colorFor(name), CornerRadii.EMPTY, null)));
            Label initials = new Label(initials(name));
            initials.getStyleClass().add(width < 60 ? "initials-small" : "initials");
            getChildren().add(initials);
        } else {
            Label placeholder = new Label("Sin imagen");
            placeholder.getStyleClass().add("muted");
            getChildren().add(placeholder);
        }

        sensitiveBadge.getStyleClass().add("sensitive-badge");
        StackPane.setAlignment(sensitiveBadge, Pos.TOP_RIGHT);
        if (width >= 60) { // en miniaturas no cabe la etiqueta: basta con el desenfoque
            getChildren().add(sensitiveBadge);
        }
        setRevealed(false);
    }

    /** Variante redonda (avatar) para las filas de rutas. */
    public static CoverView avatar(Path file, boolean sensitive, double size, String name) {
        CoverView view = new CoverView(file, sensitive, size, size, name);
        if (view.imageView.getImage() != null) {
            // Las imágenes de personaje son verticales: cargamos la imagen con el ANCHO del círculo
            // (alto 0 = el que salga) y la alineamos arriba, para que se vea la cara.
            view.imageView.setImage(new Image(file.toUri().toString(), size, 0, true, true, true));
            StackPane.setAlignment(view.imageView, Pos.TOP_CENTER);
        }
        view.setClip(new Circle(size / 2, size / 2, size / 2));
        return view;
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

    /** "Rin Tohsaka" → "RT"; "Saber" → "Sa". */
    static String initials(String name) {
        String[] words = name.strip().split("\\s+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        String word = words[0];
        return word.length() >= 2 ? word.substring(0, 1).toUpperCase() + word.substring(1, 2) : word.toUpperCase();
    }

    /**
     * Color apagado que depende del nombre: el mismo nombre da siempre el mismo color.
     * hashCode() convierte el texto en un número; lo usamos como tono (0-360 grados) en HSB.
     */
    static Color colorFor(String name) {
        double hue = Math.floorMod(name.hashCode(), 360);
        return Color.hsb(hue, 0.35, 0.42);
    }
}

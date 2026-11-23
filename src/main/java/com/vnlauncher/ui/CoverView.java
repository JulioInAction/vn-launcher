package com.vnlauncher.ui;

import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Una imagen (portada o personaje) de tamaño fijo que:
 * <ul>
 *   <li>se carga en segundo plano (no congela la interfaz con muchas portadas),</li>
 *   <li>si no hay imagen, muestra las iniciales del nombre sobre un color propio de ese nombre
 *       (o un hueco vacío si no se da nombre),</li>
 *   <li>si es sensible, se difumina y lleva una etiqueta, hasta que se llame a setRevealed(true).</li>
 * </ul>
 * StackPane apila sus hijos uno encima de otro: imagen abajo, etiqueta "Sensible" encima.
 *
 * <p>Aquí no hay ningún color: el fondo de las iniciales es una clase CSS (avatar-1 ... avatar-6)
 * cuyos colores define styles.css para cada tema.
 */
public class CoverView extends StackPane {

    /** Radio del desenfoque. 63 es el máximo que admite GaussianBlur. */
    private static final double BLUR_RADIUS = 63;
    /** Número de colores de avatar definidos en styles.css (.avatar-1 ... .avatar-6). */
    private static final int AVATAR_COLORS = 6;

    private final ImageView imageView = new ImageView();
    private final Label sensitiveBadge = new Label("Sensible");
    static final String MISSING = "No encontrado";
    /** Un círculo con «!» dentro, de 12 px. */
    private static final String MISSING_ICON = "M6 0.8A5.2 5.2 0 1 0 6.01 0.8Z M6 3.3V6.6 M6 8.7V8.8";
    private final boolean sensitive;

    /** Imagen guardada en disco (o null). */
    public CoverView(Path file, boolean sensitive, double width, double height) {
        this(file, sensitive, width, height, null);
    }

    public CoverView(Path file, boolean sensitive, double width, double height, String name) {
        this(file != null && Files.exists(file) ? file.toUri().toString() : null,
                sensitive, width, height, name);
    }

    /**
     * @param imageUrl  URL de la imagen: "file:/..." (descargada) o "https://..." (resultados de
     *                  búsqueda, que no se guardan). null = sin imagen.
     * @param sensitive si debe mostrarse difuminada por defecto
     * @param name      nombre para las iniciales cuando no hay imagen (null = hueco vacío)
     */
    public CoverView(String imageUrl, boolean sensitive, double width, double height, String name) {
        this.sensitive = sensitive;
        getStyleClass().add("cover");
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        // Recorte con esquinas redondeadas (radio 10 = 20 de "arco"). Sin él, la imagen tendría
        // esquinas cuadradas y el desenfoque "se derramaría" por fuera como un halo.
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        setClip(clip);

        if (imageUrl != null) {
            // Parámetros: url, ancho, alto, conservar proporción, suavizado, cargar en segundo plano.
            // Pedir ya el tamaño final ahorra memoria: no guardamos la imagen original entera.
            // Se pide a la resolución REAL de la pantalla (con Windows al 150 %, 1,5 veces más
            // píxeles) y se muestra al tamaño lógico con fitWidth/fitHeight: así no se amplía una
            // imagen pequeña, que se vería borrosa. preserveRatio: la imagen se ve ENTERA dentro del
            // marco; si su proporción no coincide, queda una franja del color de fondo.
            double scale = outputScale();
            imageView.setImage(new Image(imageUrl, width * scale, height * scale, true, true, true));
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            getChildren().add(imageView);
        } else if (name != null && !name.isBlank()) {
            getStyleClass().add(avatarClass(name));
            Label initials = new Label(initials(name));
            initials.getStyleClass().add(width < 60 ? "initials-small" : "initials");
            getChildren().add(initials);
        }

        sensitiveBadge.getStyleClass().add("sensitive-badge");
        StackPane.setAlignment(sensitiveBadge, Pos.TOP_RIGHT);
        if (width >= 60) { // en miniaturas no cabe la etiqueta: basta con el desenfoque
            getChildren().add(sensitiveBadge);
        }
        setRevealed(false);
    }

    /**
     * Marca la portada con «ⓘ No encontrado» arriba a la derecha (library-*.png): el
     * ejecutable del juego ya no está. Si también lleva «Sensible», esta baja un poco.
     */
    public CoverView markMissing() {
        SVGPath icon = new SVGPath();
        icon.setContent(MISSING_ICON);
        icon.getStyleClass().add("missing-badge-icon");
        Label badge = new Label(MISSING, icon);
        badge.getStyleClass().add("missing-badge");
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        StackPane.setMargin(badge, new Insets(8, 8, 0, 0));
        StackPane.setMargin(sensitiveBadge, new Insets(40, 0, 0, 0));
        getChildren().add(badge);
        return this;
    }

    /** Variante redonda (avatar) para las filas de rutas. */
    public static CoverView avatar(Path file, boolean sensitive, double size, String name) {
        CoverView view = new CoverView(file, sensitive, size, size, name);
        if (view.imageView.getImage() != null) {
            // Las imágenes de personaje son verticales: cargamos la imagen con el ANCHO del círculo
            // (alto 0 = el que salga) y la alineamos arriba, para que se vea la cara.
            view.imageView.setImage(new Image(file.toUri().toString(), size * outputScale(), 0, true, true, true));
            view.imageView.setFitHeight(0); // solo manda el ancho; el alto, el que salga
            StackPane.setAlignment(view.imageView, Pos.TOP_CENTER);
        }
        view.setClip(new Circle(size / 2, size / 2, size / 2));
        return view;
    }

    /**
     * Escala de la pantalla: 1 al 100 %, 1,25 al 125 %, 1,5 al 150 % (escalado de Windows). Si hay
     * varias pantallas, la mayor: la ventana se puede mover de una a otra.
     */
    static double outputScale() {
        double max = 1;
        for (Screen screen : Screen.getScreens()) {
            max = Math.max(max, screen.getOutputScaleX());
        }
        return max;
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

    /**
     * Si es sensible: clic para mostrarla u ocultarla (con cursor de mano y una pista). Si no lo
     * es, no hace nada. Nunca se destapa sola: solo cuando el usuario hace clic.
     */
    public CoverView revealOnClick() {
        if (sensitive) {
            setCursor(Cursor.HAND);
            Tooltip.install(this, new Tooltip("Clic para mostrar u ocultar la imagen"));
            setOnMouseClicked(event -> setRevealed(sensitiveBadge.isVisible()));
        }
        return this;
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
     * Clase CSS de color para un nombre: el mismo nombre da siempre la misma clase.
     * hashCode() convierte el texto en un número; floorMod lo deja entre 0 y 5 (aunque sea negativo).
     */
    static String avatarClass(String name) {
        return "avatar-" + (Math.floorMod(name.hashCode(), AVATAR_COLORS) + 1);
    }
}

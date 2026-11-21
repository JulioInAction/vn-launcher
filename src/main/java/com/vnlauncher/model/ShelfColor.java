package com.vnlauncher.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los 8 colores que puede tener una estantería (docs/diseño/capturas/estanteria-dialogo-*.png).
 * En la base de datos se guarda la clave («rosa»); en la interfaz se pinta con la clase CSS
 * {@code shelf-rosa}, que usa el color -vn-shelf-rosa del tema.
 */
public enum ShelfColor {
    PINK("rosa", "Rosa"),
    BLUE("azul", "Azul"),
    ORANGE("naranja", "Naranja"),
    GREEN("verde", "Verde"),
    PURPLE("morado", "Morado"),
    TEAL("turquesa", "Turquesa"),
    GREY("gris", "Gris"),
    CORAL("coral", "Coral");

    private final String key;
    private final String label;

    ShelfColor(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Lo que se guarda en shelf.color. */
    public String key() {
        return key;
    }

    /** Para el tooltip de cada muestra de color. */
    public String label() {
        return label;
    }

    /** Clase CSS del cuadradito de color: «shelf-rosa». */
    public String styleClass() {
        return "shelf-" + key;
    }

    public static Optional<ShelfColor> fromKey(String key) {
        return Arrays.stream(values()).filter(c -> c.key.equals(key)).findFirst();
    }
}

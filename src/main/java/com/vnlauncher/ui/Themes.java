package com.vnlauncher.ui;

import com.vnlauncher.model.Theme;
import javafx.scene.Node;
import javafx.scene.Parent;

/**
 * Aplica el tema claro u oscuro. En styles.css todos los colores son variables definidas en
 * ".root" (oscuro) y redefinidas en ".root.tema-claro"; así que cambiar de tema es tan simple
 * como poner o quitar la clase "tema-claro" en el nodo raíz. JavaFX recalcula el CSS al momento.
 */
final class Themes {

    static final String LIGHT_CLASS = "tema-claro";

    private Themes() {
    }

    static void apply(Parent root, Theme theme) {
        root.getStyleClass().remove(LIGHT_CLASS); // remove + add: nunca queda repetida
        if (theme == Theme.LIGHT) {
            root.getStyleClass().add(LIGHT_CLASS);
        }
    }

    /** Tema que tiene ahora mismo la ventana donde está {@code node}. */
    static Theme current(Node node) {
        boolean light = node != null && node.getScene() != null
                && node.getScene().getRoot().getStyleClass().contains(LIGHT_CLASS);
        return light ? Theme.LIGHT : Theme.DARK;
    }
}

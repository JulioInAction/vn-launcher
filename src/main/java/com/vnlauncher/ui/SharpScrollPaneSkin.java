package com.vnlauncher.ui;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.ScrollPaneSkin;

/**
 * Skin de ScrollPane que mantiene el texto nítido (suavizado LCD).
 *
 * <p>El skin normal de JavaFX activa la caché de su "viewport" (setCache(true)) para que
 * desplazarse sea más rápido: el contenido se dibuja una vez en una imagen y al desplazarse se
 * mueve esa imagen. Pero el texto dibujado en una caché pierde el suavizado LCD y sale en gris, así
 * que TODO lo que va dentro de un ScrollPane (la ficha entera, la biblioteca, Ajustes, el campo de
 * notas de un TextArea...) se veía menos nítido que la barra lateral.
 *
 * <p>Este skin es el mismo, pero sin esa caché. Se aplica a todos los ScrollPane desde styles.css
 * con {@code -fx-skin}, también a los que crean otros controles por dentro (el de un TextArea).
 * JavaFX lo crea por reflexión, por eso es público, tiene este constructor y el paquete se exporta
 * a javafx.controls en module-info.java.
 */
public class SharpScrollPaneSkin extends ScrollPaneSkin {

    public SharpScrollPaneSkin(ScrollPane control) {
        super(control);
        // El viewport es un hijo del skin con la clase CSS "viewport"
        for (Node child : getChildren()) {
            if (child.getStyleClass().contains("viewport")) {
                child.setCache(false);
            }
        }
    }
}

package com.vnlauncher.ui;

import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;

/**
 * Interruptor de sí/no con forma de pastilla (design.md): un ToggleButton sin texto cuyo
 * "botoncito" (thumb) se va a la derecha al activarlo. Los colores están en styles.css (.switch).
 */
class Switch extends ToggleButton {

    Switch() {
        getStyleClass().setAll("switch"); // setAll: sin el estilo general de .toggle-button
        Region thumb = new Region();
        thumb.getStyleClass().add("switch-thumb");
        setGraphic(thumb);
        updateAlignment();
        selectedProperty().addListener((obs, was, now) -> updateAlignment());
    }

    private void updateAlignment() {
        setAlignment(isSelected() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    }
}

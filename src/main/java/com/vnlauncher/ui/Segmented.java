package com.vnlauncher.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Botones segmentados ("Oscuro | Claro | Según Windows"): un grupo de ToggleButton del que
 * siempre hay exactamente uno pulsado. DISEÑO.md, "Ajustes, diálogos y estados".
 *
 * <p>Genérico: {@code Segmented<Theme>}, {@code Segmented<RouteStatus>}... El valor elegido está
 * en {@link #valueProperty()}, que se puede escuchar.
 *
 * @param <T> tipo de las opciones
 */
class Segmented<T> extends HBox {

    private final ToggleGroup group = new ToggleGroup();
    private final Map<T, ToggleButton> buttons = new HashMap<>();
    private final ObjectProperty<T> value = new SimpleObjectProperty<>();

    /**
     * @param options opciones en orden
     * @param label   texto de cada opción
     */
    Segmented(List<T> options, Function<T, String> label) {
        getStyleClass().add("segmented");
        setMinWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE); // nunca más alto que sus botones (ver settings-view.fxml)
        for (T option : options) {
            ToggleButton button = new ToggleButton(label.apply(option));
            button.setToggleGroup(group);
            button.setUserData(option); // guardamos la opción en el botón para recuperarla luego
            // Que nunca se encoja por debajo de su texto (si falta sitio, que ceda la explicación)
            button.setMinWidth(Region.USE_PREF_SIZE);
            buttons.put(option, button);
            getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // Un ToggleGroup permite "despulsar" el botón activo; aquí no queremos que quede ninguno
                group.selectToggle(oldToggle);
                return;
            }
            value.set(optionOf(newToggle));
        });
    }

    @SuppressWarnings("unchecked") // getUserData() devuelve Object; sabemos que es un T porque lo pusimos
    private T optionOf(Toggle toggle) {
        return (T) toggle.getUserData();
    }

    ObjectProperty<T> valueProperty() {
        return value;
    }

    T getValue() {
        return value.get();
    }

    void setValue(T option) {
        group.selectToggle(buttons.get(option));
    }
}

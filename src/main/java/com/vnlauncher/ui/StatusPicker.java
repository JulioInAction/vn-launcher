package com.vnlauncher.ui;

import com.vnlauncher.model.PlayStatus;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Estado de la VN en la ficha: una pastilla al principio de la fila de chips
 * (vn-detail-*.png) que dice el estado que se ve y si es automático o fijado a mano
 * («● Jugando · automático ⌄»). Al pulsarla se abre la lista (vn-detail-status-*.png): «Automático
 * (ahora: Jugando)» y los cinco estados.
 *
 * <p>La lista es un ContextMenu: trae de serie el moverse con las flechas, Enter, Esc y cerrarse
 * al pulsar fuera, y hereda los colores del tema de la ventana (un Popup normal no).
 */
final class StatusPicker extends Button {

    static final String AUTOMATIC = "Automático";
    /** «Según tus rutas y partidas (ahora: Jugando)» */
    static final String AUTOMATIC_HINT = "Según tus rutas y partidas (ahora: %s)";
    static final String MANUAL_HINT = "Fijado a mano";
    /** Lo que se ve en la pastilla detrás del estado: «En pausa · fijado». */
    static final String MANUAL_SUFFIX = "· fijado";
    static final String AUTOMATIC_SUFFIX = "· automático";

    private static final String CHEVRON = "M1 1 L6 6 L11 1";
    private static final String CHECK = "M1 5.5 L4.5 9 L11 1.5";
    private static final double ROW_WIDTH = 390;

    /** Recibe lo elegido: un estado, o null para «Automático». */
    private final Consumer<PlayStatus> onChoose;
    private final ContextMenu menu = new ContextMenu();
    private PlayStatus override;
    private PlayStatus automatic = PlayStatus.PENDING;

    StatusPicker(Consumer<PlayStatus> onChoose) {
        this.onChoose = onChoose;
        getStyleClass().add("status-picker");
        menu.getStyleClass().add("status-menu");
        setOnAction(event -> openMenu());
        setValue(null, PlayStatus.PENDING);
    }

    /**
     * Lo que hay que enseñar.
     *
     * @param override  el estado fijado a mano, o null si es automático
     * @param automatic el que sale con las rutas y partidas (se ve si no hay ninguno a mano)
     */
    void setValue(PlayStatus override, PlayStatus automatic) {
        this.override = override;
        this.automatic = automatic;
        PlayStatus shown = PlayStatus.effective(override, automatic);
        Label name = new Label(shown.label());
        name.getStyleClass().add("status-picker-name");
        Label how = new Label(override != null ? MANUAL_SUFFIX : AUTOMATIC_SUFFIX);
        how.getStyleClass().add("muted");
        SVGPath chevron = new SVGPath();
        chevron.setContent(CHEVRON);
        chevron.getStyleClass().add("status-picker-chevron");
        HBox content = new HBox(8, StatusVisuals.dot(shown), name, how, chevron);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setMargin(chevron, new javafx.geometry.Insets(0, 0, 0, 4));
        setGraphic(content);
        setText(null);
    }

    private void openMenu() {
        menu.getItems().setAll(menuItems(override, automatic, onChoose));
        menu.show(this, Side.BOTTOM, 0, 6);
    }

    /**
     * Las filas de la lista: «Automático (ahora: Jugando)», un separador y los cinco estados. Las
     * usa también el menú de clic derecho de las portadas (LibraryTileMenu), como submenú.
     *
     * @param onChoose recibe lo elegido (un estado, o null para «Automático»); no se llama si se
     *                 elige lo que ya estaba
     */
    static List<MenuItem> menuItems(PlayStatus override, PlayStatus automatic, Consumer<PlayStatus> onChoose) {
        List<MenuItem> items = new ArrayList<>();
        items.add(item(automaticIcon(), AUTOMATIC, String.format(AUTOMATIC_HINT, automatic.label()),
                override == null, null, override, onChoose));
        items.add(new SeparatorMenuItem());
        for (PlayStatus status : PlayStatus.values()) {
            // En pausa y Abandonada nunca salen solos: siempre son «Fijado a mano»
            items.add(item(StatusVisuals.dot(status), status.label(),
                    status.manualOnly() ? MANUAL_HINT : null, override == status, status, override, onChoose));
        }
        return items;
    }

    /** Una fila: icono, nombre, explicación debajo (si hay) y ✓ si es el elegido. */
    private static CustomMenuItem item(Node icon, String title, String hint, boolean selected, PlayStatus value,
                                       PlayStatus override, Consumer<PlayStatus> onChoose) {
        Label name = new Label(title);
        name.getStyleClass().add("status-menu-title");
        VBox texts = new VBox(1, name);
        if (hint != null) {
            Label sub = new Label(hint);
            sub.getStyleClass().add("status-menu-hint");
            texts.getChildren().add(sub);
        }
        // El icono, en un hueco fijo: así todos los nombres empiezan a la misma altura
        StackPane iconBox = new StackPane(icon);
        iconBox.setMinWidth(14);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(12, iconBox, texts, grow);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("status-menu-row");
        // Todas las filas igual de anchas (la de «Automático» es la más larga): así el ✓ queda
        // siempre en el borde derecho
        row.setMinWidth(ROW_WIDTH);
        row.setPrefWidth(ROW_WIDTH);
        if (selected) {
            SVGPath check = new SVGPath();
            check.setContent(CHECK);
            check.getStyleClass().add("status-menu-check");
            row.getChildren().add(check);
        }
        CustomMenuItem item = new CustomMenuItem(row);
        if (selected) {
            item.getStyleClass().add("selected");
        }
        item.setOnAction(event -> {
            if (value != override) {
                onChoose.accept(value);
            }
        });
        return item;
    }

    /** Aro de puntos para «Automático»: el estado lo pone la app, ni lleno ni vacío. */
    private static Node automaticIcon() {
        Circle ring = new Circle(4.5);
        ring.getStyleClass().add("status-auto-icon");
        return ring;
    }
}

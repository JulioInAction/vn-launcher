package com.vnlauncher.ui;

import com.vnlauncher.model.VnTag;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.function.Consumer;

/**
 * Etiquetas de la ficha (ficha-simplificada-*.png), sin rótulo: solo las que quepan en UNA línea
 * (como mucho {@link #COLLAPSED_MAX}) y, si quedan más, el enlace «+N más», que despliega el resto
 * y pasa a «Mostrar menos». Pulsar una etiqueta abre la biblioteca filtrada por ella.
 *
 * <p>Cuántas caben depende del ancho de la columna, así que se recalcula cada vez que cambia. Todas
 * están siempre en el FlowPane y las que no caben solo se ocultan (visible y managed a false): así
 * se pueden medir y «+N más» nunca sale de la escena (no pierde el foco del teclado).
 */
final class TagChips extends FlowPane {

    /** Como mucho, cuántas se ven antes de pulsar «+N más». */
    static final int COLLAPSED_MAX = 5;
    private static final double GAP = 8;
    /** «+52 más» */
    static final String MORE = "+%d más";
    static final String LESS = "Mostrar menos";

    private final Button toggle = new Button();
    private final Consumer<VnTag> onTagClicked;
    private List<Button> chips = List.of();
    private List<VnTag> tags = List.of();
    /** false en la vista previa: la novela no está en la biblioteca, no hay nada que filtrar. */
    private boolean clickable = true;
    private boolean expanded;

    /** @param onTagClicked qué hacer al pulsar una etiqueta (ir a la biblioteca filtrada) */
    TagChips(Consumer<VnTag> onTagClicked) {
        super(GAP, 10);
        this.onTagClicked = onTagClicked;
        toggle.getStyleClass().add("tag-more-link");
        toggle.setMinWidth(Region.USE_PREF_SIZE);
        toggle.setOnAction(event -> {
            expanded = !expanded;
            fit();
        });
        widthProperty().addListener((obs, old, width) -> fit());
        setTags(List.of());
    }

    /**
     * Las etiquetas a mostrar, ya filtradas ({@link com.vnlauncher.model.VnDetails#tagsToShow}) y de
     * mayor a menor puntuación. Sin ninguna, se oculta.
     */
    void setTags(List<VnTag> tags) {
        this.tags = tags;
        chips = tags.stream().map(this::chip).toList();
        expanded = false;
        getChildren().setAll(chips);
        getChildren().add(toggle);
        setVisible(!tags.isEmpty());
        setManaged(!tags.isEmpty());
        fit();
        // Los chips recién creados aún no tienen su CSS (ni su ancho de verdad): otra vez al pintarlos
        Platform.runLater(this::fit);
    }

    /** Si pulsar una etiqueta abre la biblioteca filtrada. Las vuelve a crear (con el mismo desplegado). */
    void setClickable(boolean clickable) {
        if (this.clickable != clickable) {
            this.clickable = clickable;
            setTags(tags);
        }
    }

    /** Enseña las que quepan en la línea (o todas, desplegado) y pone el texto de «+N más». */
    private void fit() {
        int shown = expanded ? chips.size() : fitting(getWidth());
        for (int i = 0; i < chips.size(); i++) {
            setShown(chips.get(i), i < shown);
        }
        int hidden = chips.size() - shown;
        toggle.setText(expanded ? LESS : String.format(MORE, hidden));
        setShown(toggle, expanded || hidden > 0);
    }

    /** Cuántas caben en {@code width}, dejando sitio para «+N más» si no caben todas. */
    private int fitting(double width) {
        if (width <= 0) {
            return Math.min(COLLAPSED_MAX, chips.size()); // aún sin ancho: se recalcula al tenerlo
        }
        int all = Math.min(COLLAPSED_MAX, chips.size());
        int withoutLink = countFitting(width, all);
        if (withoutLink == chips.size()) {
            return withoutLink; // caben todas: sin enlace
        }
        toggle.setText(String.format(MORE, chips.size())); // el más largo posible, para medir
        double linkWidth = toggle.prefWidth(-1) + GAP;
        return Math.max(1, countFitting(width - linkWidth, all)); // al menos una
    }

    private int countFitting(double width, int max) {
        double used = 0;
        for (int i = 0; i < max; i++) {
            double next = used + (i > 0 ? GAP : 0) + chips.get(i).prefWidth(-1);
            if (next > width) {
                return i;
            }
            used = next;
        }
        return max;
    }

    private static void setShown(Button node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private Button chip(VnTag tag) {
        Button chip = new Button(tag.name());
        chip.getStyleClass().add("tag-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE); // nunca "Bucle tem..."
        if (clickable) {
            chip.setTooltip(new Tooltip("Ver las novelas de tu biblioteca con esta etiqueta"));
            chip.setOnAction(event -> onTagClicked.accept(tag));
        } else {
            chip.getStyleClass().add("tag-chip-static");
            chip.setFocusTraversable(false);
        }
        return chip;
    }
}

package com.vnlauncher.ui;

import com.vnlauncher.model.VnTag;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * «Etiquetas» de la ficha (ficha-capturas-derecha-*.png): las 8 mejor puntuadas como chips y, si
 * hay más, un chip con borde discontinuo «+N más» que despliega el resto y pasa a «Mostrar menos».
 * Pulsar una etiqueta abre la biblioteca filtrada por ella.
 *
 * <p>Los chips van en un {@link FlowPane}: se colocan en fila y saltan a la línea siguiente cuando no
 * caben. Al desplegar solo se añaden los chips que faltan delante de «+N más» (no se rehace todo):
 * así ese botón no desaparece de la escena y no pierde el foco del teclado.
 */
final class TagChips extends VBox {

    /** Cuántas se ven antes de pulsar «+N más». */
    static final int COLLAPSED = 8;

    private final FlowPane flow = new FlowPane(8, 10);
    private final Button toggle = new Button();
    private final Consumer<VnTag> onTagClicked;
    /** Chips que solo se ven desplegado (de la novena en adelante). */
    private List<Button> extraChips = List.of();
    private boolean expanded;

    /** @param onTagClicked qué hacer al pulsar una etiqueta (ir a la biblioteca filtrada) */
    TagChips(Consumer<VnTag> onTagClicked) {
        super(10);
        this.onTagClicked = onTagClicked;
        Label heading = new Label("Etiquetas");
        heading.getStyleClass().add("tags-label");
        toggle.getStyleClass().addAll("tag-chip", "tag-more");
        toggle.setMinWidth(Region.USE_PREF_SIZE);
        toggle.setOnAction(event -> setExpanded(!expanded));
        getChildren().addAll(heading, flow);
        setTags(List.of());
    }

    /**
     * Las etiquetas a mostrar, ya filtradas ({@link com.vnlauncher.model.VnDetails#tagsToShow}) y de mayor a menor puntuación.
     * Sin ninguna, la sección entera se oculta.
     */
    void setTags(List<VnTag> tags) {
        List<Button> chips = tags.stream().map(this::chip).toList();
        extraChips = chips.subList(Math.min(COLLAPSED, chips.size()), chips.size());
        expanded = false;
        flow.getChildren().setAll(chips.subList(0, Math.min(COLLAPSED, chips.size())));
        if (!extraChips.isEmpty()) {
            flow.getChildren().add(toggle);
        }
        updateToggleText();
        setVisible(!tags.isEmpty());
        setManaged(!tags.isEmpty());
    }

    private void setExpanded(boolean value) {
        expanded = value;
        if (expanded) {
            flow.getChildren().addAll(COLLAPSED, extraChips); // delante de «+N más»
        } else {
            flow.getChildren().removeAll(extraChips);
        }
        updateToggleText();
    }

    private void updateToggleText() {
        toggle.setText(expanded ? "Mostrar menos" : "+" + extraChips.size() + " más");
    }

    private Button chip(VnTag tag) {
        Button chip = new Button(tag.name());
        chip.getStyleClass().add("tag-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE); // nunca "Bucle tem..."
        chip.setTooltip(new Tooltip("Ver las novelas de tu biblioteca con esta etiqueta"));
        chip.setOnAction(event -> onTagClicked.accept(tag));
        return chip;
    }
}

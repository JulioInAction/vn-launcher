package com.vnlauncher.ui;

import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 * Texto largo recortado a unas líneas (con "..." al final) y un enlace «Leer más» que lo despliega
 * entero; al desplegarlo, el enlace pasa a «Leer menos».
 *
 * <p>Cómo se recorta: a un Label con wrapText se le pone un alto máximo, y JavaFX corta él solo lo
 * que no cabe y añade "...". El alto de N líneas se mide con un {@link Text} de prueba con la misma
 * fuente e interlineado (así no depende de cuánto mida la fuente en cada sistema). Con otro Text de
 * prueba se mide el texto entero: el enlace solo aparece si de verdad no cabe.
 *
 * <p>Se vuelve a medir cuando cambia el ancho o la fuente (la fuente la pone el CSS después de
 * crearse el nodo).
 */
final class ExpandableText extends VBox {

    private final Label label = new Label();
    private final Hyperlink toggle = new Hyperlink("Leer más");
    private final int lines;
    private boolean expanded;

    /**
     * @param text  el texto; si es null o vacío se muestra "Sin descripción."
     * @param lines cuántas líneas se ven antes de pulsar «Leer más»
     */
    ExpandableText(String text, int lines) {
        super(4);
        this.lines = lines;
        boolean empty = text == null || text.isBlank();
        label.setText(empty ? "Sin descripción." : text);
        label.getStyleClass().add("description");
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMaxWidth(Double.MAX_VALUE);

        toggle.setOnAction(event -> {
            expanded = !expanded;
            update();
        });
        setAlignment(Pos.TOP_LEFT);
        getChildren().addAll(label, toggle);

        InvalidationListener remeasure = observable -> update();
        label.widthProperty().addListener(remeasure);
        label.fontProperty().addListener(remeasure);
        label.lineSpacingProperty().addListener(remeasure);
        update();
    }

    /** Recorta (o no) el texto y muestra el enlace solo si hace falta. */
    private void update() {
        double width = label.getWidth() - label.snappedLeftInset() - label.snappedRightInset();
        if (width <= 0) {
            // Aún sin colocar: se recorta por si acaso; al tener ancho se vuelve a medir
            label.setMaxHeight(linesHeight() + label.snappedTopInset() + label.snappedBottomInset());
            showToggle(false);
            return;
        }
        double collapsed = linesHeight();
        boolean overflows = textHeight(label.getText(), width) > collapsed + 1;
        showToggle(overflows);
        if (overflows && !expanded) {
            // +1: margen por redondeos, para que la última línea no se pierda por medio píxel
            label.setMaxHeight(Math.ceil(collapsed) + 1 + label.snappedTopInset() + label.snappedBottomInset());
        } else {
            label.setMaxHeight(Region.USE_COMPUTED_SIZE); // un Label mide como mucho su texto entero
        }
        toggle.setText(expanded ? "Leer menos" : "Leer más");
    }

    private void showToggle(boolean show) {
        toggle.setVisible(show);
        toggle.setManaged(show);
    }

    /** Alto de {@code lines} líneas con la fuente y el interlineado del Label. */
    private double linesHeight() {
        return textHeight("X\n".repeat(lines - 1) + "X", 0);
    }

    /** Alto de un texto con la fuente y el interlineado del Label; wrappingWidth 0 = sin ajustar. */
    private double textHeight(String text, double wrappingWidth) {
        Text probe = new Text(text);
        probe.setFont(label.getFont());
        probe.setLineSpacing(label.getLineSpacing());
        probe.setWrappingWidth(wrappingWidth);
        return probe.getLayoutBounds().getHeight();
    }

    /** Para los tests: si ahora mismo se ve el enlace. */
    boolean canExpand() {
        return toggle.isVisible();
    }
}

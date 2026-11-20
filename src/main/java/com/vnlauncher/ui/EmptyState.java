package com.vnlauncher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * Estado vacío (docs/diseño/capturas/vacio-*.png): icono, título, explicación y, si tiene sentido,
 * un botón para hacer algo. DISEÑO.md: "siempre explican qué ha pasado y qué hacer".
 */
final class EmptyState {

    /** Icono de la lupa (caja de 24x24). */
    static final String SEARCH_ICON = "M11 4A7 7 0 1 0 11.01 4Z M16 16L21 21";

    private EmptyState() {
    }

    /**
     * @param icon   icono (ver {@link #svgIcon} y {@link #booksIcon}) o null
     * @param action botón (o null)
     * @param lines  párrafos de explicación
     */
    static VBox create(Node icon, String title, Button action, String... lines) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        if (icon != null) {
            VBox.setMargin(icon, new Insets(0, 0, 8, 0)); // un poco más de aire bajo el icono
            box.getChildren().add(icon);
        }
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");
        t.setWrapText(true);
        box.getChildren().add(t);
        for (String line : lines) {
            Label l = new Label(line);
            l.getStyleClass().add("empty-text");
            l.setWrapText(true);
            l.setMaxWidth(440);
            box.getChildren().add(l);
        }
        if (action != null) {
            VBox.setMargin(action, new Insets(16, 0, 0, 0));
            box.getChildren().add(action);
        }
        return box;
    }

    /** Icono de trazo fino (24x24) a escala {@code scale}. */
    static Node svgIcon(String path, double scale) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.getStyleClass().add("empty-icon");
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        return new Group(svg); // el Group exterior mide el icono ya escalado
    }

    /** El icono de los dos libros del logo (64x64), en tono apagado. */
    static Node booksIcon() {
        Group books = new Group();
        String[][] parts = {
                {"M13 6H33A5 5 0 0 1 38 11V43A5 5 0 0 1 33 48H13A5 5 0 0 1 8 43V11A5 5 0 0 1 13 6Z", "4"},
                {"M15 8V46", "3"},
                {"M43 14H50A5 5 0 0 1 55 19V53A5 5 0 0 1 50 58H26A5 5 0 0 1 21 53V52.5", "4"}};
        for (String[] part : parts) {
            SVGPath p = new SVGPath();
            p.setContent(part[0]);
            p.setStrokeWidth(Double.parseDouble(part[1]));
            p.getStyleClass().add("empty-logo-stroke");
            books.getChildren().add(p);
        }
        SVGPath bookmark = new SVGPath();
        bookmark.setContent("M24 4V21L28 17.5L32 21V4Z");
        bookmark.getStyleClass().add("empty-logo-fill");
        books.getChildren().add(bookmark);
        return new Group(books);
    }
}

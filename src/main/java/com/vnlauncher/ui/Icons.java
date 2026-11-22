package com.vnlauncher.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

import java.util.Locale;

/** Iconos dibujados en código (sin imágenes ni fuentes de iconos), con el color del tema. */
final class Icons {

    /** Engranaje de 20 px: 8 dientes y el agujero del centro, con trazo. */
    private static final String GEAR = gearPath(8, 12, 12, 9.5, 7.2, 3);
    /** Cuerpo del lápiz (con la punta) y la raya de la goma. */
    private static final String PENCIL = "M15.6 4.4 A2.1 2.1 0 0 1 18.6 7.4 L8.2 17.8 L4 19 L5.2 14.8 Z M14 6 L17 9";

    private Icons() {
    }

    /** «Opciones del juego». */
    static Node gear() {
        SVGPath gear = new SVGPath();
        gear.setContent(GEAR);
        gear.getStyleClass().add("icon-stroke");
        return gear;
    }

    /**
     * Lápiz («Editar»), con trazo como el engranaje: el cuerpo inclinado con la punta abajo a la
     * izquierda y una raya donde empieza la goma. Caja de 24 × 24, unos 17 px de lado a lado.
     */
    static Node pencil() {
        SVGPath pencil = new SVGPath();
        pencil.setContent(PENCIL);
        pencil.getStyleClass().add("icon-stroke");
        return pencil;
    }

    /**
     * «+» de 12 px dibujado con dos trazos. Un «+» de texto no sirve en un botón redondo: la fuente
     * lo coloca según la línea base del texto y queda más bajo que el centro.
     */
    static Node plus() {
        SVGPath plus = new SVGPath();
        plus.setContent("M6 0V12 M0 6H12");
        plus.getStyleClass().add("icon-plus");
        return plus;
    }

    /** «i» dentro de un círculo, de 16 px (avisos suaves como «No está en tu biblioteca»). */
    static Node info() {
        SVGPath info = new SVGPath();
        info.setContent("M8 1.5A6.5 6.5 0 1 0 8.01 1.5Z M8 7V11.2 M8 4.6V4.8");
        info.getStyleClass().add("icon-info");
        return info;
    }

    /** «⋯» (Más opciones): tres puntos. */
    static Node moreDots() {
        HBox dots = new HBox(4);
        dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            Circle dot = new Circle(2);
            dot.getStyleClass().add("icon-fill");
            dots.getChildren().add(dot);
        }
        return dots;
    }

    /**
     * Trazado de un engranaje centrado en (cx, cy): un polígono que alterna dientes (radio
     * {@code outer}) y huecos (radio {@code inner}), más un círculo de radio {@code hole}. Se
     * calcula con trigonometría en vez de copiar un icono: así es propio y se puede ajustar.
     */
    static String gearPath(int teeth, double cx, double cy, double outer, double inner, double hole) {
        StringBuilder path = new StringBuilder();
        double step = 2 * Math.PI / teeth;
        double toothHalf = step * 0.2;   // la mitad del ancho de la punta del diente
        double flank = step * 0.08;      // los lados del diente, un poco inclinados
        for (int i = 0; i < teeth; i++) {
            double a = i * step - Math.PI / 2;
            point(path, i == 0 ? "M" : "L", cx, cy, inner, a - toothHalf - flank);
            point(path, "L", cx, cy, outer, a - toothHalf);
            point(path, "L", cx, cy, outer, a + toothHalf);
            point(path, "L", cx, cy, inner, a + toothHalf + flank);
        }
        path.append("Z ");
        // Círculo del centro: dos medios arcos
        path.append(String.format(Locale.ROOT, "M%.2f %.2fA%.2f %.2f 0 1 0 %.2f %.2fA%.2f %.2f 0 1 0 %.2f %.2fZ",
                cx - hole, cy, hole, hole, cx + hole, cy, hole, hole, cx - hole, cy));
        return path.toString();
    }

    private static void point(StringBuilder path, String command, double cx, double cy, double r, double angle) {
        path.append(String.format(Locale.ROOT, "%s%.2f %.2f ", command,
                cx + r * Math.cos(angle), cy + r * Math.sin(angle)));
    }
}

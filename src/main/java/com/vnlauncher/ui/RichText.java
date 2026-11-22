package com.vnlauncher.ui;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * Texto con trozos en negrita (en TextFlow), a partir de plantillas completas con marcas
 * {@code <b>…</b>}: «<b>37 min</b> jugados». Así cada frase sigue siendo UNA plantilla (traducible
 * entera) y no un pegado de trozos sueltos.
 */
final class RichText {

    private static final String OPEN = "<b>";
    private static final String CLOSE = "</b>";
    /** Entre las partes de una línea de datos: «37 min jugados · última partida…». */
    private static final String SEPARATOR = "   ·   ";

    private RichText() {
    }

    /**
     * Los trozos de una frase: lo normal con la clase {@code rich-text} y lo marcado con
     * {@code rich-strong}.
     */
    static List<Text> parse(String marked) {
        List<Text> parts = new ArrayList<>();
        int from = 0;
        while (from < marked.length()) {
            int open = marked.indexOf(OPEN, from);
            if (open < 0) {
                parts.add(text(marked.substring(from), "rich-text"));
                break;
            }
            if (open > from) {
                parts.add(text(marked.substring(from, open), "rich-text"));
            }
            int close = marked.indexOf(CLOSE, open);
            int end = close < 0 ? marked.length() : close;
            parts.add(text(marked.substring(open + OPEN.length(), end), "rich-strong"));
            from = close < 0 ? marked.length() : close + CLOSE.length();
        }
        return parts;
    }

    /**
     * Varias frases para una fila, separadas por «·» en -vn-faint (clase {@code rich-separator}):
     * un bloque por frase, con su separador al final. Puestos en un FlowPane, si no caben saltan de
     * línea entre una frase y otra, nunca a mitad de frase.
     *
     * @return una lista que se puede modificar (p. ej. para añadir algo pegado a la última frase)
     */
    static List<Node> blocks(List<String> sentences) {
        List<Node> blocks = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            TextFlow block = new TextFlow();
            block.getChildren().addAll(parse(sentences.get(i)));
            if (i < sentences.size() - 1) {
                block.getChildren().add(text(SEPARATOR, "rich-separator"));
            }
            // Que el FlowPane no la estreche: así nunca se parte por dentro
            block.setMinWidth(Region.USE_PREF_SIZE);
            blocks.add(block);
        }
        return blocks;
    }

    private static Text text(String content, String styleClass) {
        Text text = new Text(content);
        text.getStyleClass().add(styleClass);
        return text;
    }
}

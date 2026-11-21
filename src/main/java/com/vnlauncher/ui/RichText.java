package com.vnlauncher.ui;

import javafx.scene.Node;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Texto con trozos en negrita para un TextFlow, a partir de plantillas completas con marcas
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

    /** Varias frases en una línea, separadas por «·» en -vn-faint (clase {@code rich-separator}). */
    static List<Node> line(List<String> sentences) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            if (i > 0) {
                nodes.add(text(SEPARATOR, "rich-separator"));
            }
            nodes.addAll(parse(sentences.get(i)));
        }
        return nodes;
    }

    private static Text text(String content, String styleClass) {
        Text text = new Text(content);
        text.getStyleClass().add(styleClass);
        return text;
    }
}

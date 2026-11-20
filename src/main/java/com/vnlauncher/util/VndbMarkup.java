package com.vnlauncher.util;

import java.util.regex.Pattern;

/**
 * Convierte el formato de texto de VNDB (parecido al BBCode de los foros) a texto plano.
 *
 * <p>Ejemplo: {@code "[b]Hola[/b] [url=https://x.com]web[/url]"} → {@code "Hola web"}.
 * Los bloques {@code [spoiler]...[/spoiler]} se eliminan enteros: no queremos destripar nada.
 */
public final class VndbMarkup {

    // (?s) = el punto también encaja con saltos de línea (un spoiler puede ocupar varios párrafos).
    // (?i) = sin distinguir mayúsculas/minúsculas.
    // .*? = "perezoso": coge lo MÍNIMO posible, para que dos spoilers seguidos no se traguen
    //       el texto que hay entre ellos.
    private static final Pattern SPOILER = Pattern.compile("(?is)\\[spoiler].*?\\[/spoiler]");
    // [url=LO_QUE_SEA]texto[/url] -> nos quedamos con el grupo 1 (el texto visible)
    private static final Pattern URL = Pattern.compile("(?is)\\[url=[^]]*](.*?)\\[/url]");
    // Etiquetas simples de apertura/cierre: [b] [/b] [i] [/i] ...
    private static final Pattern SIMPLE_TAGS = Pattern.compile("(?i)\\[/?(b|i|u|s|quote|raw|code)]");
    // 3 o más saltos de línea seguidos (con espacios entre medias) -> uno en blanco
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n\\s*\\n(\\s*\\n)+");

    private VndbMarkup() {
    }

    public static String toPlainText(String markup) {
        if (markup == null) {
            return null;
        }
        String text = SPOILER.matcher(markup).replaceAll("");
        text = URL.matcher(text).replaceAll("$1");
        text = SIMPLE_TAGS.matcher(text).replaceAll("");
        text = EXTRA_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }
}

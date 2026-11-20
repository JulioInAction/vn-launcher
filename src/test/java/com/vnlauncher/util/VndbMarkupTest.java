package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VndbMarkupTest {

    @Test
    void keepsTextOfLinks() {
        assertEquals("[Based on the official site]",
                VndbMarkup.toPlainText("[Based on [url=https://key.visualarts.gr.jp/]the official site[/url]]"));
    }

    @Test
    void removesSpoilersCompletely() {
        assertEquals("Empieza. Termina.",
                VndbMarkup.toPlainText("Empieza. [spoiler]El asesino es\nel mayordomo[/spoiler]Termina."));
    }

    @Test
    void twoSpoilersDoNotSwallowTextBetweenThem() {
        assertEquals("A  B  C", VndbMarkup.toPlainText("A [spoiler]x[/spoiler] B [spoiler]y[/spoiler] C"));
    }

    @Test
    void removesSimpleTagsCaseInsensitive() {
        assertEquals("negrita y cursiva", VndbMarkup.toPlainText("[b]negrita[/b] y [I]cursiva[/I]"));
    }

    @Test
    void collapsesExtraBlankLines() {
        assertEquals("Párrafo 1\n\nPárrafo 2", VndbMarkup.toPlainText("Párrafo 1\n\n\n\nPárrafo 2\n"));
    }

    @Test
    void nullStaysNull() {
        assertNull(VndbMarkup.toPlainText(null));
    }
}

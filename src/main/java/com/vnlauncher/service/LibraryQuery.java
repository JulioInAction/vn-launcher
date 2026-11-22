package com.vnlauncher.service;

import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.VnTag;
import com.vnlauncher.model.VisualNovel;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Los filtros de la biblioteca, todos juntos y sin nada de interfaz (por eso se pueden probar):
 * <ul>
 *   <li>un estado <b>o</b> una estantería (son excluyentes: elegir uno quita el otro);</li>
 *   <li>una etiqueta, que se combina con cualquiera de los dos;</li>
 *   <li>un texto de búsqueda (título, título original o desarrollador), que también se combina.</li>
 * </ul>
 * Es inmutable: cada "with" devuelve otro filtro.
 *
 * @param status estado elegido en la barra lateral, o null
 * @param shelf  estantería elegida en la barra lateral, o null
 * @param tag    etiqueta pulsada en una ficha, o null
 * @param text   lo escrito en «Buscar en tu biblioteca», o null/vacío
 */
public record LibraryQuery(PlayStatus status, Shelf shelf, VnTag tag, String text) {

    /** Sin filtros: toda la biblioteca. */
    public static final LibraryQuery ALL = new LibraryQuery(null, null, null, null);

    /** Solo este estado (quita la estantería, si había). */
    public LibraryQuery withStatus(PlayStatus status) {
        return new LibraryQuery(status, status == null ? shelf : null, tag, text);
    }

    /** Solo esta estantería (quita el estado, si había). */
    public LibraryQuery withShelf(Shelf shelf) {
        return new LibraryQuery(shelf == null ? status : null, shelf, tag, text);
    }

    public LibraryQuery withTag(VnTag tag) {
        return new LibraryQuery(status, shelf, tag, text);
    }

    public LibraryQuery withText(String text) {
        return new LibraryQuery(status, shelf, tag, text);
    }

    /** true si no filtra nada. */
    public boolean isEmpty() {
        return status == null && shelf == null && tag == null && !hasText();
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    /**
     * Las VNs que pasan todos los filtros, en el mismo orden.
     *
     * @param inShelf las VNs de la estantería elegida (se ignora si no hay estantería)
     * @param withTag las VNs con la etiqueta elegida (se ignora si no hay etiqueta)
     */
    public List<LibraryItem> apply(List<LibraryItem> items, Set<String> inShelf, Set<String> withTag) {
        String needle = hasText() ? normalize(text) : null;
        return items.stream()
                .filter(item -> status == null || item.status() == status)
                .filter(item -> shelf == null || inShelf.contains(item.entry().vn().id()))
                .filter(item -> tag == null || withTag.contains(item.entry().vn().id()))
                .filter(item -> needle == null || matches(item.entry().vn(), needle))
                .toList();
    }

    private static boolean matches(VisualNovel vn, String needle) {
        return contains(vn.title(), needle) || contains(vn.altTitle(), needle)
                || contains(vn.developerNames(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && normalize(haystack).contains(needle);
    }

    /**
     * El texto tal como se compara: sin mayúsculas, sin tildes y con el ancho completo pasado a normal
     * («ＦＡＴＥ» = «fate», «ｶﾅ» = «カナ»), así que «Clannad», «clánnad» y «ＣＬＡＮＮＡＤ» coinciden. Solo se
     * quitan las marcas de las letras latinas (U+0300–U+036F): el dakuten del japonés («ド» ≠ «ト») se queda.
     */
    static String normalize(String s) {
        String wide = Normalizer.normalize(s.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(wide, Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "");
        return Normalizer.normalize(decomposed, Normalizer.Form.NFC);
    }
}

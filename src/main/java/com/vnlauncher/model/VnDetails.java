package com.vnlauncher.model;

import java.util.List;

/**
 * Datos de la ficha que se piden aparte de la VN: sus etiquetas y sus capturas de pantalla.
 *
 * @param tags        todas (también las spoiler y las sexuales: la ficha decide cuáles enseña), de
 *                    mayor a menor puntuación
 * @param screenshots en el orden de VNDB
 */
public record VnDetails(List<VnTag> tags, List<VnScreenshot> screenshots) {

    public VnDetails {
        tags = List.copyOf(tags);
        screenshots = List.copyOf(screenshots);
    }

    public static final VnDetails EMPTY = new VnDetails(List.of(), List.of());

    /**
     * Las etiquetas que enseña la ficha: sin las spoiler (salvo con «Mostrar spoilers») y sin las
     * de contenido sexual (salvo con «Difuminar imágenes: No difuminar»: VNDB también las oculta
     * por defecto). Mantiene el orden, de mayor a menor puntuación.
     */
    public List<VnTag> tagsToShow(boolean showSpoilers, boolean showSexual) {
        return tags.stream()
                .filter(tag -> showSpoilers || !tag.isSpoiler())
                .filter(tag -> showSexual || !tag.isSexual())
                .toList();
    }
}

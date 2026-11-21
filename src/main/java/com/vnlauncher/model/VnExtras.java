package com.vnlauncher.model;

import java.util.List;

/**
 * Lo que se pide al seleccionar un resultado de la búsqueda: etiquetas, capturas y la relación
 * más importante con otra VN.
 *
 * @param tags        etiquetas SIN spoilers, de mayor a menor puntuación
 * @param screenshots las primeras capturas de pantalla
 * @param relation    "Secuela de Fate/stay night", "Fandisc de..."; null si no tiene relaciones
 */
public record VnExtras(List<Tag> tags, List<Screenshot> screenshots, String relation) {

    public VnExtras {
        tags = List.copyOf(tags);
        screenshots = List.copyOf(screenshots);
    }

    /** @param category "cont" (contenido), "ero" (sexual) o "tech" (técnica) */
    public record Tag(String name, double rating, String category) {
        public boolean isSexual() {
            return "ero".equals(category);
        }
    }

    /** Miniatura y valoración (0..2) para difuminarla si es sensible. */
    public record Screenshot(String thumbnailUrl, String url, Double sexual, Double violence) {
    }
}

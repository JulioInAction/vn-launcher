package com.vnlauncher.api.dto;

/**
 * Imagen de VNDB (portada o personaje).
 *
 * @param sexual   media de los votos de contenido sexual: 0 = seguro, 1 = sugerente, 2 = explícito.
 * @param violence media de los votos de violencia: 0 = ninguna, 1 = moderada, 2 = brutal.
 *                 Son Double (no double) para que un valor ausente sea null y no 0.
 */
public record ImageDto(String url, Double sexual, Double violence) {
}

package com.vnlauncher.api.dto;

import java.util.List;

/**
 * Una edición (release) de una VN tal como llega del endpoint /release de VNDB: solo los campos
 * que pedimos para la galería de portadas.
 *
 * @param languages idiomas de la edición
 * @param images    portadas, contraportadas, discos... de esta edición (puede ser null o vacía)
 */
public record ReleaseDto(
        String id,
        String title,
        String released,
        List<LanguageDto> languages,
        List<ReleaseImageDto> images
) {
}

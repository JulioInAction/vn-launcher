package com.vnlauncher.api.dto;

import java.util.List;

/**
 * Una imagen de una edición.
 *
 * @param id        "cv82903"
 * @param url       imagen a tamaño completo
 * @param thumbnail miniatura (256 px de ancho como mucho)
 * @param type      "pkgfront" (caja frontal), "pkgback", "pkgside", "pkgmed" (disco), "pkgcontent", "dig" (digital)
 * @param languages idiomas de la imagen; null = los de la edición
 * @param vn        VN a la que corresponde si la edición incluye varias; null = a todas
 * @param sexual    0..2 (media de votos)
 * @param violence  0..2
 */
public record ReleaseImageDto(
        String id,
        String url,
        String thumbnail,
        String type,
        List<String> languages,
        String vn,
        Double sexual,
        Double violence
) {
}

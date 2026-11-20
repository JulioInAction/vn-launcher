package com.vnlauncher.api.dto;

import java.util.List;

/**
 * Una VN tal como llega en el JSON de VNDB. Los nombres de los componentes coinciden con los
 * del JSON (por eso "alttitle" va en minúsculas), así Jackson los rellena sin anotaciones.
 * Cualquier campo puede venir a null (p. ej. image si la VN no tiene portada).
 */
public record VnDto(
        String id,
        String title,
        String alttitle,
        String released,
        String description,
        ImageDto image,
        List<ProducerDto> developers
) {
}

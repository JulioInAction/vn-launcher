package com.vnlauncher.api.dto;

import java.util.List;

/**
 * Un personaje tal como llega del endpoint /character de VNDB.
 *
 * @param vns VNs en las que aparece, con su rol y nivel de spoiler EN CADA UNA.
 *            Ojo: la misma VN puede aparecer varias veces (una por versión/release).
 */
public record CharacterDto(
        String id,
        String name,
        String original,
        String description,
        ImageDto image,
        List<CharacterVnDto> vns
) {
}

package com.vnlauncher.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Una VN tal como llega en el JSON de VNDB. Los nombres de los componentes coinciden con los
 * del JSON (por eso "alttitle" va en minúsculas), así Jackson los rellena sin anotaciones.
 * Cualquier campo puede venir a null (p. ej. image si la VN no tiene portada).
 *
 * @param olang         idioma original ("ja")
 * @param lengthMinutes duración media según los votos de los usuarios; null si no hay bastantes
 * @param rating        valoración de 10 a 100; null si no hay votos
 * @param devstatus     0 = terminada, 1 = en desarrollo, 2 = cancelada
 */
public record VnDto(
        String id,
        String title,
        String alttitle,
        String released,
        String description,
        ImageDto image,
        List<ProducerDto> developers,
        // Campos "ligeros" para el panel de la búsqueda: vienen en la misma petición.
        // @JsonProperty: en el JSON se llaman con guion bajo y en Java preferimos camelCase.
        String olang,
        @JsonProperty("length_minutes") Integer lengthMinutes,
        @JsonProperty("length_votes") Integer lengthVotes,
        Double rating,
        Integer votecount,
        List<String> languages,
        List<String> platforms,
        Integer devstatus
) {
}

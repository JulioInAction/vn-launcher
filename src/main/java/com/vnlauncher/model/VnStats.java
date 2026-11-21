package com.vnlauncher.model;

import java.util.List;

/**
 * Datos de VNDB que solo se usan en el panel de la búsqueda (no se guardan en la base de datos).
 * Vienen en la misma petición que la búsqueda.
 *
 * @param originalLanguage idioma original ("ja"), para ponerlo el primero
 * @param lengthMinutes    duración media según los votos; null si no hay bastantes
 * @param lengthVotes      cuántos votos de duración hay
 * @param rating           valoración de 10 a 100; null si no hay votos
 * @param voteCount        número de votos de la valoración
 * @param languages        códigos de idioma ("ja", "en", "zh-Hans"...)
 * @param platforms        códigos de plataforma ("win", "psv", "swi"...)
 * @param devStatus        0 = terminada, 1 = en desarrollo, 2 = cancelada; null si no se sabe
 */
public record VnStats(String originalLanguage, Integer lengthMinutes, Integer lengthVotes, Double rating,
                      Integer voteCount, List<String> languages, List<String> platforms, Integer devStatus) {

    public VnStats {
        languages = languages == null ? List.of() : List.copyOf(languages);
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
    }

    /** Sin ningún dato (p. ej. en tests). */
    public static final VnStats EMPTY = new VnStats(null, null, null, null, null, null, null, null);
}

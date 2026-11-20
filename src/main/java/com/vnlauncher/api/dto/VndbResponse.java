package com.vnlauncher.api.dto;

import java.util.List;

/**
 * Envoltorio común de todas las respuestas de VNDB: {"results": [...], "more": true|false}.
 * Es genérico (T = VnDto, CharacterDto...) porque la forma es igual en todos los endpoints.
 *
 * @param more true si hay más páginas de resultados.
 */
public record VndbResponse<T>(List<T> results, boolean more) {
}

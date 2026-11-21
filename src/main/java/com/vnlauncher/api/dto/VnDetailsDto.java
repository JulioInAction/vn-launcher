package com.vnlauncher.api.dto;

import java.util.List;

/**
 * Datos "pesados" de una VN para el panel de la búsqueda: se piden solo al seleccionar un
 * resultado (una petición a /vn filtrada por su id).
 */
public record VnDetailsDto(
        String id,
        List<TagDto> tags,
        List<ScreenshotDto> screenshots,
        List<RelationDto> relations
) {
}

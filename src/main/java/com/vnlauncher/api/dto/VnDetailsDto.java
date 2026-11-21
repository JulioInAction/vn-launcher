package com.vnlauncher.api.dto;

import java.util.List;

/**
 * Datos "pesados" de una VN (una petición a /vn filtrada por su id). Los usan el panel de la
 * búsqueda (al seleccionar un resultado, con relaciones) y la ficha (etiquetas y capturas, sin
 * relaciones: entonces relations es null).
 */
public record VnDetailsDto(
        String id,
        List<TagDto> tags,
        List<ScreenshotDto> screenshots,
        List<RelationDto> relations
) {
}

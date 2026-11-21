package com.vnlauncher.api.dto;

/**
 * Una etiqueta de una VN.
 *
 * @param rating   puntuación 0..3 (cuánto se aplica)
 * @param spoiler  0 = no es spoiler, 1 = menor, 2 = mayor
 * @param lie      true si la etiqueta es "mentira" (engañosa a propósito): no se muestra
 * @param category "cont" (contenido), "ero" (sexual) o "tech" (técnica)
 */
public record TagDto(String id, String name, Double rating, Integer spoiler, Boolean lie, String category) {
}

package com.vnlauncher.model;

/**
 * Una VN de la biblioteca junto con datos calculados: su estado, el recuento de sus rutas y el
 * tiempo jugado (para la barra lateral, los filtros, el orden y las tarjetas de la biblioteca).
 *
 * @param playSeconds suma de las sesiones de juego terminadas
 */
public record LibraryItem(LibraryEntry entry, PlayStatus status, RouteSummary routes, long playSeconds) {
}

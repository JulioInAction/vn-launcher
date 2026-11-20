package com.vnlauncher.model;

/**
 * Una VN de la biblioteca junto con su estado calculado y el recuento de sus rutas
 * (para la barra lateral, los filtros y las tarjetas de la biblioteca).
 */
public record LibraryItem(LibraryEntry entry, PlayStatus status, RouteSummary routes) {
}

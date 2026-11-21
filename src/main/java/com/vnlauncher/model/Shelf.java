package com.vnlauncher.model;

/**
 * Una estantería del usuario («Favoritas», «Para la Steam Deck»...).
 *
 * @param position orden en la barra lateral (0 = la primera)
 * @param novels   cuántas VNs tenía al leerla de la base de datos (el contador de la barra)
 */
public record Shelf(long id, String name, ShelfColor color, int position, int novels) {
}

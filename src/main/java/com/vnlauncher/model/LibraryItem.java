package com.vnlauncher.model;

/**
 * Una VN de la biblioteca junto con datos calculados: su estado, el recuento de sus rutas y el
 * tiempo jugado (para la barra lateral, los filtros, el orden y las tarjetas de la biblioteca).
 *
 * @param automaticStatus el estado calculado con sus rutas y partidas (ver {@link PlayStatus#of})
 * @param playSeconds     suma de las sesiones de juego terminadas
 * @param exeMissing      true si tiene ejecutable pero el archivo ya no está («No encontrado»)
 */
public record LibraryItem(LibraryEntry entry, PlayStatus automaticStatus, RouteSummary routes, long playSeconds,
                          boolean exeMissing) {

    /** El estado que se ve: el fijado a mano en la ficha, o si no hay, el calculado. */
    public PlayStatus status() {
        return PlayStatus.effective(entry.statusOverride(), automaticStatus);
    }

    /** true si el estado lo ha fijado el usuario a mano. */
    public boolean manualStatus() {
        return entry.statusOverride() != null;
    }
}

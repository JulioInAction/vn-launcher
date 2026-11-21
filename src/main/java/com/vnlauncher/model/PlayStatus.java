package com.vnlauncher.model;

import java.time.Instant;
import java.util.OptionalInt;

/**
 * Estado de una VN de la biblioteca (el bloque «Estado» de la barra lateral).
 *
 * <p>Normalmente se CALCULA a partir de sus rutas y de si se ha jugado ({@link #of}); solo da tres
 * de los cinco valores:
 * <ul>
 *   <li>Completada: tiene rutas y todas están completadas.</li>
 *   <li>Jugando: alguna ruta en curso o completada, o se ha lanzado alguna vez.</li>
 *   <li>Pendiente: el resto.</li>
 * </ul>
 * Desde la ficha se puede fijar a mano cualquiera de los cinco (En pausa y Abandonada, solo así).
 * El fijado a mano manda sobre el calculado ({@link #effective}) hasta que se vuelve a «Automático».
 */
public enum PlayStatus {
    PLAYING("Jugando", "Jugando", 1),
    PENDING("Pendiente", "Pendientes", 0),
    COMPLETED("Completada", "Completadas", 2),
    PAUSED("En pausa", "En pausa", 3),
    DROPPED("Abandonada", "Abandonadas", 4);

    private final String label;
    private final String shelfName;
    /** Lista de VNDB equivalente; 0 = ninguna. */
    private final int vndbLabel;

    PlayStatus(String label, String shelfName, int vndbLabel) {
        this.label = label;
        this.shelfName = shelfName;
        this.vndbLabel = vndbLabel;
    }

    /** Para una VN: «Jugando», «Pendiente», «En pausa»... (pastillas, selector de la ficha). */
    public String label() {
        return label;
    }

    /** Para el grupo: «Jugando», «Pendientes», «Abandonadas»... (barra lateral, filtros). */
    public String shelfName() {
        return shelfName;
    }

    /**
     * La lista de VNDB que le corresponde (Playing 1, Finished 2, Stalled 3, Dropped 4), para
     * cuando se sincronice con la cuenta de VNDB. Pendiente no tiene: en VNDB sería «Wishlist» o
     * nada, y no es lo mismo que tenerla y no haber empezado.
     */
    public OptionalInt vndbLabelId() {
        return vndbLabel == 0 ? OptionalInt.empty() : OptionalInt.of(vndbLabel);
    }

    /** true para los que solo se pueden fijar a mano (nunca salen solos): En pausa y Abandonada. */
    public boolean manualOnly() {
        return this == PAUSED || this == DROPPED;
    }

    /** El estado calculado. Solo devuelve PLAYING, PENDING o COMPLETED. */
    public static PlayStatus of(RouteSummary routes, Instant lastPlayedAt) {
        if (routes.total() > 0 && routes.completed() == routes.total()) {
            return COMPLETED;
        }
        if (routes.inProgress() > 0 || routes.completed() > 0 || lastPlayedAt != null) {
            return PLAYING;
        }
        return PENDING;
    }

    /**
     * El que se ve: el fijado a mano si lo hay; si no, el calculado.
     *
     * @param override  el fijado a mano, o null (automático)
     */
    public static PlayStatus effective(PlayStatus override, PlayStatus automatic) {
        return override != null ? override : automatic;
    }
}

package com.vnlauncher.model;

import java.time.Instant;

/**
 * Estado de una VN de la biblioteca (las "estanterías" de la barra lateral). Se CALCULA a partir
 * de sus rutas y de si se ha jugado; no se guarda en la base de datos:
 * <ul>
 *   <li>Completada: tiene rutas y todas están completadas.</li>
 *   <li>Jugando: alguna ruta en curso o completada, o se ha lanzado alguna vez.</li>
 *   <li>Pendiente: el resto.</li>
 * </ul>
 */
public enum PlayStatus {
    PLAYING("Jugando"),
    PENDING("Pendientes"),
    COMPLETED("Completadas");

    private final String shelfName;

    PlayStatus(String shelfName) {
        this.shelfName = shelfName;
    }

    /** Nombre de la estantería: "Jugando", "Pendientes", "Completadas". */
    public String shelfName() {
        return shelfName;
    }

    public static PlayStatus of(RouteSummary routes, Instant lastPlayedAt) {
        if (routes.total() > 0 && routes.completed() == routes.total()) {
            return COMPLETED;
        }
        if (routes.inProgress() > 0 || routes.completed() > 0 || lastPlayedAt != null) {
            return PLAYING;
        }
        return PENDING;
    }
}

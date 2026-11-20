package com.vnlauncher.model;

/**
 * Estado de una ruta. El nombre de la constante (PENDING...) es lo que se guarda en la BD
 * (la columna route.status tiene un CHECK con estos tres valores); {@link #label()} es el
 * texto que ve el usuario.
 */
public enum RouteStatus {
    PENDING("Pendiente"),
    IN_PROGRESS("En curso"),
    COMPLETED("Completada");

    private final String label;

    // Los enums pueden tener constructor y campos: cada constante se crea con su texto.
    RouteStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

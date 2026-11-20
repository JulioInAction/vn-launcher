package com.vnlauncher.model;

/** Recuento de las rutas de una VN por estado (sale de una consulta con GROUP BY). */
public record RouteSummary(int total, int completed, int inProgress) {

    public static final RouteSummary NONE = new RouteSummary(0, 0, 0);
}

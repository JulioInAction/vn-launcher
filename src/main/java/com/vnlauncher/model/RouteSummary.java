package com.vnlauncher.model;

import java.util.List;

/** Recuento de las rutas de una VN por estado (sale de una consulta con GROUP BY). */
public record RouteSummary(int total, int completed, int inProgress) {

    public static final RouteSummary NONE = new RouteSummary(0, 0, 0);

    /** El mismo recuento hecho con las rutas ya cargadas (la ficha las tiene en memoria). */
    public static RouteSummary of(List<Route> routes) {
        int completed = (int) routes.stream().filter(r -> r.status() == RouteStatus.COMPLETED).count();
        int inProgress = (int) routes.stream().filter(r -> r.status() == RouteStatus.IN_PROGRESS).count();
        return new RouteSummary(routes.size(), completed, inProgress);
    }
}

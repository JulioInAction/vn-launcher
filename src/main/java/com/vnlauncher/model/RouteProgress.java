package com.vnlauncher.model;

/** Progreso de las rutas de una VN, p. ej. 2 de 5 completadas. */
public record RouteProgress(int completed, int total) {

    /** Fracción 0..1 para una barra de progreso (0 si no hay rutas). */
    public double fraction() {
        return total == 0 ? 0 : (double) completed / total;
    }

    @Override
    public String toString() {
        return completed + "/" + total;
    }
}

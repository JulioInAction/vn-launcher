package com.vnlauncher.model;

import java.time.Instant;

/**
 * Una ruta de una VN, creada por el usuario (VNDB no tiene datos de rutas).
 *
 * @param id               null si aún no se ha guardado (lo genera la BD con AUTOINCREMENT)
 * @param characterId      heroína de la ruta; null para rutas sin heroína (común, true end...)
 * @param recommendedOrder posición en el orden recomendado: 1, 2, 3... (la gestiona RouteService)
 * @param completedAt      cuándo se marcó como completada; null si no lo está
 */
public record Route(
        Long id,
        String vnId,
        String characterId,
        String name,
        int recommendedOrder,
        RouteStatus status,
        String notes,
        Instant completedAt
) {
    /**
     * Los records son inmutables: para "cambiar" un campo se crea una copia con ese campo distinto.
     * Estos métodos "with..." lo hacen más legible que llamar al constructor con 8 parámetros.
     */
    public Route withOrder(int newOrder) {
        return new Route(id, vnId, characterId, name, newOrder, status, notes, completedAt);
    }

    public Route withStatus(RouteStatus newStatus, Instant newCompletedAt) {
        return new Route(id, vnId, characterId, name, recommendedOrder, newStatus, notes, newCompletedAt);
    }

    public boolean isCompleted() {
        return status == RouteStatus.COMPLETED;
    }
}

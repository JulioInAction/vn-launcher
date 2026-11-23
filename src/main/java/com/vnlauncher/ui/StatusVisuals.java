package com.vnlauncher.ui;

import com.vnlauncher.model.PlayStatus;
import javafx.scene.shape.Circle;

/**
 * Cómo se pinta cada estado, igual en la barra lateral, las tarjetas y la ficha
 * (docs/design/mockups/library-*.png y vn-detail-status-*.png):
 * naranja Jugando, gris Pendiente, azul Completada, un aro sin relleno En pausa y rojo Abandonada.
 */
final class StatusVisuals {

    private StatusVisuals() {
    }

    /** El punto de color (7 px). En pausa es un aro: está parada, ni hecha ni por hacer. */
    static Circle dot(PlayStatus status) {
        Circle dot = new Circle(3.5);
        dot.getStyleClass().addAll("status-dot", switch (status) {
            case PLAYING -> "dot-playing";
            case PENDING -> "dot-pending";
            case COMPLETED -> "dot-completed";
            case PAUSED -> "dot-paused";
            case DROPPED -> "dot-dropped";
        });
        return dot;
    }

    /** Clase de la pastilla de las tarjetas: misma paleta que los puntos. */
    static String pillClass(PlayStatus status) {
        return switch (status) {
            case PLAYING -> "pill-in-progress";
            case PENDING -> "pill-pending";
            case COMPLETED -> "pill-completed";
            case PAUSED -> "pill-paused";
            case DROPPED -> "pill-dropped";
        };
    }
}

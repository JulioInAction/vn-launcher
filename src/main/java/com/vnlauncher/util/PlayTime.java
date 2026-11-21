package com.vnlauncher.util;

/** Formatea tiempos de juego para la interfaz. */
public final class PlayTime {

    private PlayTime() {
    }

    /** Formato largo para la ficha: "36 h 20 min", "45 min", "0 min". */
    public static String format(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;   // % = resto de la división
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }

    /** Total de un perfil entero: «312 h», o en minutos si no llega a una hora («45 min», «0 min»). */
    public static String formatTotal(long seconds) {
        return seconds < 3600 ? String.format("%d min", seconds / 60) : String.format("%d h", seconds / 3600);
    }

    /** Formato corto para las tarjetas: "36 h", "45 min" o "Sin jugar". */
    public static String formatShort(long seconds) {
        if (seconds < 60) {
            return "Sin jugar";
        }
        return seconds < 3600 ? seconds / 60 + " min" : seconds / 3600 + " h";
    }
}

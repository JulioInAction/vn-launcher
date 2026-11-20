package com.vnlauncher.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Convierte un instante en texto relativo: "hace 5 min", "ayer", "hace 3 días"... */
public final class RelativeTime {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private RelativeTime() {
    }

    /**
     * @param then instante pasado (null = "Nunca")
     * @param now  instante actual (parámetro en vez de Instant.now() para poder testearlo)
     */
    public static String format(Instant then, Instant now, ZoneId zone) {
        if (then == null) {
            return "Nunca";
        }
        Duration ago = Duration.between(then, now);
        if (ago.isNegative() || ago.toMinutes() < 1) {
            return "hace un momento";
        }
        if (ago.toHours() < 1) {
            return "hace " + ago.toMinutes() + " min";
        }
        if (ago.toDays() < 1) {
            return "hace " + ago.toHours() + " h";
        }
        long days = ago.toDays();
        if (days == 1) {
            return "ayer";
        }
        if (days < 30) {
            return "hace " + days + " días";
        }
        return "el " + DATE.format(then.atZone(zone));
    }
}

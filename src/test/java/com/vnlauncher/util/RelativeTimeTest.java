package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelativeTimeTest {

    private static final Instant NOW = Instant.parse("2026-11-20T20:00:00Z");

    private static String ago(Duration d) {
        return RelativeTime.format(NOW.minus(d), NOW, ZoneOffset.UTC);
    }

    @Test
    void formatsDifferentDistances() {
        assertEquals("Nunca", RelativeTime.format(null, NOW, ZoneOffset.UTC));
        assertEquals("hace un momento", ago(Duration.ofSeconds(20)));
        assertEquals("hace 5 min", ago(Duration.ofMinutes(5)));
        assertEquals("hace 3 h", ago(Duration.ofHours(3)));
        assertEquals("ayer", ago(Duration.ofHours(30)));
        assertEquals("hace 2 días", ago(Duration.ofDays(2)));
        assertEquals("el 11/09/2026", ago(Duration.ofDays(70)));
    }
}

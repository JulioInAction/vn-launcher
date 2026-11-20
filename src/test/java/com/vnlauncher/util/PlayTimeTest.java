package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayTimeTest {

    @Test
    void longFormat() {
        assertEquals("0 min", PlayTime.format(30));
        assertEquals("45 min", PlayTime.format(45 * 60));
        assertEquals("2 h", PlayTime.format(2 * 3600));
        assertEquals("36 h 20 min", PlayTime.format(36 * 3600 + 20 * 60 + 59));
    }

    @Test
    void shortFormat() {
        assertEquals("Sin jugar", PlayTime.formatShort(0));
        assertEquals("12 min", PlayTime.formatShort(12 * 60));
        assertEquals("36 h", PlayTime.formatShort(36 * 3600 + 50 * 60));
    }
}

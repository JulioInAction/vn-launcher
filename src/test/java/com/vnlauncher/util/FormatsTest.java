package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FormatsTest {

    @Test
    void numericDateWithoutLocaleData() {
        assertEquals("21/11/2026, 10:42", Formats.DATE_TIME.format(LocalDateTime.of(2026, 11, 21, 10, 42, 3)));
        assertEquals("07/01/2027, 09:05", Formats.DATE_TIME.format(LocalDateTime.of(2027, 1, 7, 9, 5)));
        assertEquals("07/01/2027", Formats.DATE.format(LocalDateTime.of(2027, 1, 7, 9, 5)));
    }

    @Test
    void fileSizesInKbOrMbWithADecimalComma() {
        assertEquals("0 KB", Formats.fileSize(0));
        assertEquals("2 KB", Formats.fileSize(1536));
        assertEquals("1024 KB", Formats.fileSize(1024 * 1024 - 1));
        assertEquals("1,0 MB", Formats.fileSize(1024 * 1024));
        assertEquals("2,4 MB", Formats.fileSize(2_516_582));
        assertEquals("142,0 MB", Formats.fileSize(148_897_792));
    }

    @Test
    void todayOrDateAtSaysTodayOrGivesTheDate() {
        java.time.ZoneId zone = java.time.ZoneOffset.UTC;
        java.time.Instant now = java.time.Instant.parse("2026-11-22T20:00:00Z");

        assertEquals("hoy a las 18:07",
                Formats.todayOrDateAt(java.time.Instant.parse("2026-11-22T18:07:00Z"), now, zone));
        assertEquals("el 21/11/2026 a las 23:59",
                Formats.todayOrDateAt(java.time.Instant.parse("2026-11-21T23:59:00Z"), now, zone));
    }
}

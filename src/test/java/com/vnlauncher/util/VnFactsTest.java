package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VnFactsTest {

    @Test
    void lengthInHoursOrMinutesOrNoData() {
        assertEquals("unas 51 h", VnFacts.length(3060, 256), "Fate/hollow ataraxia");
        assertEquals("unas 2 h", VnFacts.length(95, 3));
        assertEquals("unos 45 min", VnFacts.length(43, 10), "Menos de hora y media: minutos de 5 en 5");
        assertEquals("unos 5 min", VnFacts.length(1, 1));
        assertEquals("Sin datos", VnFacts.length(null, null));
        assertEquals("Sin datos", VnFacts.length(600, 0), "Sin votos de duración no hay dato fiable");
    }

    @Test
    void ratingOnAScaleOfTenWithSpanishSeparators() {
        assertEquals("8,1 · 5.944 votos", VnFacts.rating(81.4, 5944));
        assertEquals("8,2 · 1.500 votos", VnFacts.rating(82.0, 1500));
        assertEquals("7,0 · 1 voto", VnFacts.rating(70.0, 1));
        assertEquals("9,1 · 123 votos", VnFacts.rating(91.04, 123));
        assertEquals("Sin votos", VnFacts.rating(null, 0));
        assertEquals("1.234.567", VnFacts.thousands(1234567));
    }

    @Test
    void languagesPutTheOriginalFirstAndAreCut() {
        List<String> hollow = List.of("en", "es", "it", "ja", "ko", "ru", "zh-Hans", "zh-Hant");
        assertEquals("Japonés, inglés, español, italiano y 4 más", VnFacts.languages(hollow, "ja"));
        assertEquals("Inglés", VnFacts.languages(List.of("en"), "ja"), "Si el original no está, no se añade");
        assertEquals("Japonés, xx", VnFacts.languages(List.of("ja", "xx"), null), "Código desconocido: tal cual");
        assertEquals("Sin datos", VnFacts.languages(List.of(), "ja"));
    }

    @Test
    void platformsWithFriendlyNames() {
        assertEquals("Windows, Android, PSP, PS Vita y 2 más",
                VnFacts.platforms(List.of("win", "and", "psp", "psv", "swi", "oth")));
        assertEquals("ABC", VnFacts.platforms(List.of("abc")));
    }

    @Test
    void developmentStatus() {
        assertEquals("Terminada", VnFacts.devStatus(0));
        assertEquals("En desarrollo", VnFacts.devStatus(1));
        assertEquals("Cancelada", VnFacts.devStatus(2));
        assertNull(VnFacts.devStatus(null));
    }
}

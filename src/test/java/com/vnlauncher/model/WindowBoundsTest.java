package com.vnlauncher.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowBoundsTest {

    @Test
    void firstTimeTakesNinetyPercentCenteredOnAFullHdScreen() {
        // 1920 × 1080 con la barra de tareas abajo (40 px): zona útil 1920 × 1040
        WindowBounds b = WindowBounds.firstTime(0, 0, 1920, 1040, 1040, 680);

        assertEquals(new WindowBounds(120, 52, 1680, 936, false), b,
                "Ancho: 90 % = 1728, con tope 1680. Alto: 90 % = 936");
    }

    @Test
    void firstTimeIsCappedOnBigScreensAndRoundedToWholePixels() {
        WindowBounds b = WindowBounds.firstTime(0, 0, 2560, 1400, 1040, 680);

        assertEquals(1680, b.width());
        assertEquals(1050, b.height());
        assertEquals(440, b.x());
        assertEquals(175, b.y());
    }

    @Test
    void firstTimeNeverGoesBelowTheMinimumUnlessTheScreenIsSmaller() {
        // Portátil con escalado al 150 %: 1366 × 728 "lógicos"
        WindowBounds b = WindowBounds.firstTime(0, 0, 1366, 728, 1040, 680);
        assertEquals(1229, b.width(), "90 % de 1366 = 1229,4 → 1229");
        assertEquals(680, b.height(), "90 % de 728 = 655 se queda corto: el mínimo es 680");

        // Pantalla más pequeña que el mínimo: se ajusta a la pantalla
        WindowBounds tiny = WindowBounds.firstTime(0, 0, 1000, 600, 1040, 680);
        assertEquals(1000, tiny.width());
        assertEquals(600, tiny.height());
    }

    @Test
    void secondMonitorKeepsItsOffset() {
        WindowBounds b = WindowBounds.firstTime(1920, 0, 1920, 1040, 1040, 680);
        assertEquals(1920 + 120, b.x());
    }
}

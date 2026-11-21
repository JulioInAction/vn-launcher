package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BoxBlurTest {

    private static final int GREY = 0xFF808080;

    @Test
    void aUniformImageStaysTheSameAndOpaqueUpToTheEdges() {
        int[] pixels = new int[8 * 5];
        Arrays.fill(pixels, GREY);

        BoxBlur.blur(pixels, 8, 5, 2, 3);

        assertTrue(Arrays.stream(pixels).allMatch(p -> p == GREY),
                "Con los bordes repetidos no entra transparencia ni oscurece las esquinas");
    }

    @Test
    void aBrightDotSpreadsToItsNeighbours() {
        int w = 9;
        int h = 9;
        int[] pixels = new int[w * h];
        Arrays.fill(pixels, 0xFF000000); // negro opaco
        pixels[4 * w + 4] = 0xFFFFFFFF;   // un punto blanco en el centro

        BoxBlur.blur(pixels, w, h, 1, 1);

        int center = pixels[4 * w + 4] & 0xFF;
        int neighbour = pixels[4 * w + 5] & 0xFF;
        int far = pixels[0] & 0xFF;
        assertEquals(255 / 9, center, "La media de 3 × 3 con un solo blanco");
        assertEquals(center, neighbour, "Con radio 1, el vecino ve el mismo punto");
        assertEquals(0, far, "Lejos no llega");
        assertEquals(0xFF, pixels[4 * w + 4] >>> 24, "Sigue opaco");
    }

    @Test
    void sizeMismatchIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BoxBlur.blur(new int[10], 3, 3, 1, 1));
    }
}

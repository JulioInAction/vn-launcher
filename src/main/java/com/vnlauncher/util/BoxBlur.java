package com.vnlauncher.util;

/**
 * Difuminado "de caja" sobre los píxeles de una imagen pequeña (formato ARGB: un int por píxel).
 * Cada píxel pasa a ser la media de los que tiene alrededor; repetirlo 3 veces da un resultado
 * casi igual a un difuminado gaussiano.
 *
 * <p>En los bordes se repite el último píxel (en vez de mezclar con "nada"): así la imagen sigue
 * siendo opaca hasta el borde. El GaussianBlur de JavaFX, en cambio, deja los bordes
 * semitransparentes.
 */
public final class BoxBlur {

    private BoxBlur() {
    }

    /**
     * Difumina {@code argb} (ancho × alto, fila a fila) en el sitio.
     *
     * @param radius cuántos píxeles a cada lado entran en la media
     * @param passes cuántas veces se repite (3 ≈ gaussiano)
     */
    public static void blur(int[] argb, int width, int height, int radius, int passes) {
        if (argb.length != width * height) {
            throw new IllegalArgumentException("El array no mide ancho × alto");
        }
        int[] tmp = new int[argb.length];
        for (int pass = 0; pass < passes; pass++) {
            blurLine(argb, tmp, width, height, radius, true);   // en horizontal: argb → tmp
            blurLine(tmp, argb, width, height, radius, false);  // en vertical:   tmp → argb
        }
    }

    /** Una pasada en una dirección: cada píxel de {@code dst} es la media de su vecindad en {@code src}. */
    private static void blurLine(int[] src, int[] dst, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;
        int count = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int j = Math.clamp(i + k, 0, length - 1); // borde: se repite el último píxel
                    int pixel = src[horizontal ? line * width + j : j * width + line];
                    a += pixel >>> 24;
                    r += (pixel >> 16) & 0xFF;
                    g += (pixel >> 8) & 0xFF;
                    b += pixel & 0xFF;
                }
                dst[horizontal ? line * width + i : i * width + line] =
                        (a / count) << 24 | (r / count) << 16 | (g / count) << 8 | (b / count);
            }
        }
    }
}

package com.vnlauncher.service;

/**
 * Decide qué imágenes se consideran "sensibles" y se muestran difuminadas por defecto.
 *
 * <p>VNDB da para cada imagen la MEDIA de los votos de los usuarios (0..2):
 * <ul>
 *   <li>sexual: 0 = seguro, 1 = sugerente, 2 = explícito</li>
 *   <li>violencia: 0 = ninguna, 1 = moderada, 2 = brutal</li>
 * </ul>
 * Por defecto difuminamos lo que supera 1, es decir, imágenes que algunos votantes ya
 * consideran explícitas o brutales. Una portada que todos votan "sugerente" (1.0) se ve normal.
 * En la Fase 5 estos umbrales serán configurables en la pantalla de ajustes.
 */
public class ContentFilter {

    public static final double DEFAULT_SEXUAL_THRESHOLD = 1.0;
    public static final double DEFAULT_VIOLENCE_THRESHOLD = 1.0;

    private final double sexualThreshold;
    private final double violenceThreshold;

    public ContentFilter(double sexualThreshold, double violenceThreshold) {
        this.sexualThreshold = sexualThreshold;
        this.violenceThreshold = violenceThreshold;
    }

    public static ContentFilter withDefaults() {
        return new ContentFilter(DEFAULT_SEXUAL_THRESHOLD, DEFAULT_VIOLENCE_THRESHOLD);
    }

    /** null = sin votos/sin imagen: no se considera sensible. */
    public boolean isSensitive(Double sexual, Double violence) {
        return (sexual != null && sexual > sexualThreshold)
                || (violence != null && violence > violenceThreshold);
    }
}

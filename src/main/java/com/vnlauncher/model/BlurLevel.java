package com.vnlauncher.model;

/**
 * Qué imágenes se difuminan (Ajustes → Contenido → Difuminar imágenes).
 *
 * <p>VNDB da la MEDIA de los votos de cada imagen: sexual 0 = seguro, 1 = sugerente,
 * 2 = explícito (y violencia 0 / 1 / 2 igual). Como es una media, se compara con un umbral a
 * medio camino: a partir de 1.5 la mayoría la considera explícita; a partir de 0.5, sugerente.
 */
public enum BlurLevel {
    NONE("ninguna", "No difuminar", Double.POSITIVE_INFINITY),
    EXPLICIT("explicitas", "Solo explícitas", 1.5),
    SUGGESTIVE("sugerentes", "Sugerentes y explícitas", 0.5);

    private final String settingValue;
    private final String label;
    /** Se difumina si la media sexual o de violencia es mayor o igual que esto. */
    private final double threshold;

    BlurLevel(String settingValue, String label, double threshold) {
        this.settingValue = settingValue;
        this.label = label;
        this.threshold = threshold;
    }

    public String settingValue() {
        return settingValue;
    }

    public String label() {
        return label;
    }

    public double threshold() {
        return threshold;
    }

    /** Por defecto, "Solo explícitas". */
    public static BlurLevel fromSetting(String value) {
        for (BlurLevel level : values()) {
            if (level.settingValue.equals(value)) {
                return level;
            }
        }
        return EXPLICIT;
    }
}

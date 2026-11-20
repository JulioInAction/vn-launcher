package com.vnlauncher.service;

import com.vnlauncher.model.BlurLevel;

/**
 * Decide qué imágenes se consideran "sensibles" y se muestran difuminadas por defecto,
 * según el nivel elegido en Ajustes (ver {@link BlurLevel} para los umbrales).
 *
 * <p>Hay una sola instancia para toda la app; al cambiar el ajuste se llama a
 * {@link #setLevel} y las pantallas que se dibujen a partir de entonces ya lo usan.
 * "volatile": se lee desde varios hilos y así todos ven siempre el último valor.
 */
public class ContentFilter {

    private volatile BlurLevel level;

    public ContentFilter(BlurLevel level) {
        this.level = level;
    }

    public static ContentFilter withDefaults() {
        return new ContentFilter(BlurLevel.EXPLICIT);
    }

    public BlurLevel level() {
        return level;
    }

    public void setLevel(BlurLevel level) {
        this.level = level;
    }

    /** null = sin votos/sin imagen: no se considera sensible. */
    public boolean isSensitive(Double sexual, Double violence) {
        double threshold = level.threshold();
        return (sexual != null && sexual >= threshold)
                || (violence != null && violence >= threshold);
    }
}

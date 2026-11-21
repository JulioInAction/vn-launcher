package com.vnlauncher.model;

/** Un resultado de la búsqueda: la VN (lo que se guarda si se añade) y los datos del panel. */
public record SearchHit(VisualNovel vn, VnStats stats) {
}

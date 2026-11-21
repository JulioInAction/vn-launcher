package com.vnlauncher.model;

/**
 * Una captura de pantalla de una VN, tal como la da VNDB (las imágenes aún no se descargan).
 *
 * @param url          imagen a tamaño completo
 * @param thumbnailUrl miniatura (puede ser null)
 * @param sexual       media de votos 0..2 (para difuminarla si es sensible); null = sin votos
 * @param violence     igual, de violencia
 */
public record VnScreenshot(String url, String thumbnailUrl, Double sexual, Double violence) {
}

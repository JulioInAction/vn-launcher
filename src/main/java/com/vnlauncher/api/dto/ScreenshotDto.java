package com.vnlauncher.api.dto;

/** Una captura de pantalla de una VN: miniatura, imagen completa y su valoración (0..2). */
public record ScreenshotDto(String url, String thumbnail, Double sexual, Double violence) {
}

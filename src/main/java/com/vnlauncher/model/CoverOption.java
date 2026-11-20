package com.vnlauncher.model;

/**
 * Una imagen de VNDB que se puede elegir como portada en la galería: la de una edición concreta
 * (caja frontal, contraportada, versión digital...).
 *
 * @param imageId      id de la imagen en VNDB ("cv82903")
 * @param url          imagen a tamaño completo (solo se descarga al confirmar)
 * @param thumbnailUrl miniatura (se descarga al abrir la galería)
 * @param releaseTitle título de la edición ("Clannad - First Press Edition")
 * @param language     idioma ("ja", "en"...) o null si no se sabe
 * @param type         tipo de VNDB: "pkgfront", "pkgback", "pkgside", "pkgmed", "pkgcontent" o "dig"
 * @param sexual       valoración 0..2, para difuminarla si es sensible
 * @param violence     valoración 0..2
 */
public record CoverOption(String imageId, String url, String thumbnailUrl, String releaseTitle,
                          String language, String type, Double sexual, Double violence) {

    /** "Caja frontal", "Digital"... */
    public String typeLabel() {
        return switch (type == null ? "" : type) {
            case "pkgfront" -> "Caja frontal";
            case "pkgback" -> "Caja trasera";
            case "pkgside" -> "Lateral";
            case "pkgmed" -> "Disco";
            case "pkgcontent" -> "Contenido";
            case "dig" -> "Digital";
            default -> "Imagen";
        };
    }

    /** "JA · Caja frontal" (o solo el tipo si no se sabe el idioma). */
    public String subtitle() {
        return language == null ? typeLabel() : language.toUpperCase() + " · " + typeLabel();
    }

    /** Orden en la galería: primero las cajas frontales y las digitales, que son las que sirven de portada. */
    public int typeOrder() {
        return switch (type == null ? "" : type) {
            case "pkgfront" -> 0;
            case "dig" -> 1;
            case "pkgback" -> 2;
            case "pkgside" -> 3;
            case "pkgmed" -> 4;
            case "pkgcontent" -> 5;
            default -> 6;
        };
    }
}

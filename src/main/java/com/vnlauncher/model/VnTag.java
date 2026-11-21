package com.vnlauncher.model;

/**
 * Una etiqueta de VNDB aplicada a una VN ("Drama", "Bucle temporal"...).
 *
 * @param id       id de VNDB ("g32"); sirve para filtrar aunque cambie el nombre
 * @param rating   cuánto se aplica, de 0 a 3 (media de los votos)
 * @param spoiler  0 = no es spoiler, 1 = spoiler menor, 2 = spoiler mayor
 * @param category "cont" (contenido), "ero" (sexual) o "tech" (técnica); puede ser null
 */
public record VnTag(String id, String name, double rating, int spoiler, String category) {

    public boolean isSpoiler() {
        return spoiler > 0;
    }

    /** Etiquetas de contenido sexual: VNDB las oculta por defecto, y aquí también. */
    public boolean isSexual() {
        return "ero".equals(category);
    }
}

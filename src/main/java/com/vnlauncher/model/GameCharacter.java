package com.vnlauncher.model;

/**
 * Un personaje de VNDB, independiente de la VN en la que aparezca.
 * (Se llama GameCharacter y no Character para no chocar con java.lang.Character.)
 *
 * @param original  nombre en su idioma original (normalmente japonés); puede ser null
 * @param imagePath imagen descargada, relativa a la carpeta de datos; null si no hay
 */
public record GameCharacter(
        String id,
        String name,
        String original,
        String description,
        String imageUrl,
        String imagePath,
        Double imageSexual,
        Double imageViolence
) {
}

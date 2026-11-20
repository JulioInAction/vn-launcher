package com.vnlauncher.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Una novela visual en el modelo de la aplicación. A diferencia de VnDto (que copia el JSON),
 * aquí los nombres siguen las convenciones de Java, la descripción ya es texto plano
 * y la lista de desarrolladores nunca es null.
 *
 * @param released     fecha tal como la da VNDB: "2004-04-28", "2004", "TBA"... o null.
 * @param imageSexual  0..2, null si no hay portada.
 * @param imageViolence 0..2, null si no hay portada.
 */
public record VisualNovel(
        String id,
        String title,
        String altTitle,
        String description,
        String released,
        String imageUrl,
        Double imageSexual,
        Double imageViolence,
        List<Developer> developers
) {
    /**
     * Constructor compacto de record: se ejecuta antes de asignar los campos.
     * Lo usamos para hacer una copia inmutable de la lista (así nadie puede modificarla desde fuera).
     */
    public VisualNovel {
        developers = developers == null ? List.of() : List.copyOf(developers);
    }

    /** "Key, Visual Arts" o cadena vacía si no hay desarrolladores. */
    public String developerNames() {
        return developers.stream().map(Developer::name).collect(Collectors.joining(", "));
    }
}

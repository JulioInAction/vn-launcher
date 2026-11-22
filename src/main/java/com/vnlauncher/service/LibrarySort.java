package com.vnlauncher.service;

import com.vnlauncher.model.LibraryItem;

import java.time.Instant;
import java.util.Comparator;

/**
 * Órdenes posibles de la biblioteca ("Ordenar por"). Cada uno lleva su Comparator: el objeto que
 * sabe decir cuál de dos elementos va antes. Se combinan con thenComparing para desempatar.
 */
public enum LibrarySort {

    LAST_PLAYED("Último jugado",
            // nullsLast: las que nunca se han jugado (lastPlayedAt null) van al final
            Comparator.comparing((LibraryItem i) -> i.entry().lastPlayedAt(),
                    Comparator.nullsLast(Comparator.<Instant>reverseOrder()))),
    // "Todos iguales" (devuelve 0): así decide el desempate por título de comparator().
    // (Una constante de enum no puede usar BY_TITLE aquí: aún no está inicializado.)
    TITLE("Título", (a, b) -> 0),
    RECENTLY_ADDED("Añadida recientemente",
            Comparator.comparing((LibraryItem i) -> i.entry().addedAt()).reversed()),
    PLAY_TIME("Tiempo jugado",
            Comparator.comparingLong(LibraryItem::playSeconds).reversed());

    /** Desempate común: por título, sin distinguir mayúsculas. */
    private static final Comparator<LibraryItem> BY_TITLE =
            Comparator.comparing(i -> i.entry().vn().title(), String.CASE_INSENSITIVE_ORDER);

    private final String label;
    private final Comparator<LibraryItem> comparator;

    LibrarySort(String label, Comparator<LibraryItem> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    /** El orden guardado en Ajustes; «Último jugado» si el texto no es ninguno de los conocidos. */
    public static LibrarySort fromSetting(String value) {
        for (LibrarySort sort : values()) {
            if (sort.name().equals(value)) {
                return sort;
            }
        }
        return LAST_PLAYED;
    }

    public String label() {
        return label;
    }

    public Comparator<LibraryItem> comparator() {
        return comparator.thenComparing(BY_TITLE);
    }
}

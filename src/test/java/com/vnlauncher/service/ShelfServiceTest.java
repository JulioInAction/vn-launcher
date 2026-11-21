package com.vnlauncher.service;

import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.ShelfColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShelfServiceTest {

    private static final Shelf FAVOURITES = new Shelf(1, "Favoritas", ShelfColor.PINK, 0, 3);
    private static final Shelf JAPANESE = new Shelf(2, "Ñoñas en japonés", ShelfColor.BLUE, 1, 0);
    private static final List<Shelf> EXISTING = List.of(FAVOURITES, JAPANESE);

    @Test
    void aNameIsNeededAndSpacesDoNotCount() {
        assertEquals(Optional.of(ShelfService.EMPTY), ShelfService.nameProblem("", EXISTING, null));
        assertEquals(Optional.of(ShelfService.EMPTY), ShelfService.nameProblem("   ", EXISTING, null));
        assertEquals(Optional.empty(), ShelfService.nameProblem("  Para la Steam Deck  ", EXISTING, null));
    }

    @Test
    void atMostFortyCharacters() {
        assertEquals(Optional.empty(), ShelfService.nameProblem("x".repeat(40), EXISTING, null));
        assertEquals(Optional.of(String.format(ShelfService.TOO_LONG, 40)),
                ShelfService.nameProblem("x".repeat(41), EXISTING, null));
    }

    @Test
    void namesCannotRepeatIgnoringCaseAlsoWithAccents() {
        assertEquals(Optional.of(ShelfService.DUPLICATE), ShelfService.nameProblem("FAVORITAS", EXISTING, null));
        assertEquals(Optional.of(ShelfService.DUPLICATE),
                ShelfService.nameProblem("ñOÑAS EN JAPONÉS", EXISTING, null), "También Ñ y É");
        assertEquals(Optional.empty(), ShelfService.nameProblem("Favoritas 2", EXISTING, null));
    }

    @Test
    void whenEditingItsOwnNameIsFine() {
        assertEquals(Optional.empty(), ShelfService.nameProblem("favoritas", EXISTING, FAVOURITES),
                "Cambiar solo las mayúsculas");
        assertEquals(Optional.of(ShelfService.DUPLICATE),
                ShelfService.nameProblem("Ñoñas en japonés", EXISTING, FAVOURITES));
    }
}

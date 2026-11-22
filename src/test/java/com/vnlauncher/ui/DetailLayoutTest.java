package com.vnlauncher.ui;

import com.vnlauncher.model.VnStats;
import com.vnlauncher.ui.DetailLayout.FactCard;
import com.vnlauncher.ui.DetailLayout.MenuEntry;
import com.vnlauncher.ui.DetailLayout.Mode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetailLayoutTest {

    private static final VnStats FULL = new VnStats("ja", 2400, 30, 82.0, 1500, List.of("en", "ja"),
            List.of("win"), 0);

    @Test
    void theLibraryFichaHasEverythingExceptTheAddButtonAndTheCards() {
        DetailLayout.Sections s = DetailLayout.sections(Mode.LIBRARY);
        assertTrue(s.statusAndShelves() && s.playActions() && s.dataLine() && s.exeNotice()
                && s.changeCover() && s.routes() && s.characterRoutes() && s.tagsClickable());
        assertFalse(s.addAction());
        assertFalse(s.factCards());
    }

    @Test
    void thePreviewOnlyHasWhatMakesSenseForANovelNotInTheLibrary() {
        DetailLayout.Sections s = DetailLayout.sections(Mode.PREVIEW);
        assertTrue(s.addAction());
        assertTrue(s.factCards());
        assertFalse(s.statusAndShelves() || s.playActions() || s.dataLine() || s.exeNotice() || s.changeCover()
                || s.routes() || s.characterRoutes() || s.tagsClickable());
    }

    @Test
    void thePreviewMenuOnlyHasViewInVndb() {
        assertEquals(List.of(MenuEntry.OPEN_VNDB), DetailLayout.menu(Mode.PREVIEW, false));
        assertEquals(List.of(MenuEntry.OPEN_VNDB), DetailLayout.menu(Mode.PREVIEW, true),
                "Aunque hubiera programa de configuración");
    }

    @Test
    void theLibraryMenuHasEverythingAndTheConfigProgramOnlyIfThereIsOne() {
        assertEquals(List.of(MenuEntry.OPEN_VNDB, MenuEntry.REFRESH, MenuEntry.REMOVE),
                DetailLayout.menu(Mode.LIBRARY, false));
        assertEquals(List.of(MenuEntry.OPEN_CONFIG, MenuEntry.OPEN_VNDB, MenuEntry.REFRESH, MenuEntry.REMOVE),
                DetailLayout.menu(Mode.LIBRARY, true));
    }

    @Test
    void allThreeCardsAreShownInOrderWithTheOriginalLanguageFirst() {
        List<FactCard> cards = DetailLayout.factCards(FULL);
        assertEquals(List.of("Duración", "Valoración", "Idiomas"), cards.stream().map(FactCard::label).toList());
        assertEquals("unas 40 h", cards.get(0).value());
        assertEquals("8,2 · 1.500 votos", cards.get(1).value());
        assertEquals("Japonés, inglés", cards.get(2).value());
    }

    @Test
    void aMissingDataDropsItsCardWithoutLeavingAGap() {
        VnStats noLength = new VnStats("ja", null, null, 82.0, 1500, List.of("ja"), List.of(), null);
        assertEquals(List.of("Valoración", "Idiomas"),
                DetailLayout.factCards(noLength).stream().map(FactCard::label).toList());

        VnStats noVotes = new VnStats("ja", 2400, 30, 80.0, 0, List.of(), List.of(), null);
        assertEquals(List.of("Duración"), DetailLayout.factCards(noVotes).stream().map(FactCard::label).toList());

        VnStats lengthWithoutVotes = new VnStats("ja", 2400, 0, null, null, List.of("ja"), List.of(), null);
        assertEquals(List.of("Idiomas"),
                DetailLayout.factCards(lengthWithoutVotes).stream().map(FactCard::label).toList());
    }

    @Test
    void withNoDataAtAllThereIsNoRow() {
        assertTrue(DetailLayout.factCards(VnStats.EMPTY).isEmpty());
    }
}

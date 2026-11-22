package com.vnlauncher.service;

import com.vnlauncher.model.Developer;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.ShelfColor;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LibraryQueryTest {

    private static final Shelf FAVOURITES = new Shelf(1, "Favoritas", ShelfColor.PINK, 0, 2);
    private static final VnTag DRAMA = new VnTag("g1", "Drama", 2.5, 0, "cont");

    private static LibraryItem item(String id, String title, String altTitle, String developer, PlayStatus status) {
        VisualNovel vn = new VisualNovel(id, title, altTitle, null, null, null, null, null, null,
                List.of(new Developer("p" + id, developer, null)));
        LibraryEntry entry = new LibraryEntry(vn, Instant.EPOCH, null, null, null, false, null, null, null);
        return new LibraryItem(entry, status, RouteSummary.NONE, 0, false, false);
    }

    private static final LibraryItem CLANNAD = item("v4", "CLANNAD", "クラナド", "Key", PlayStatus.PLAYING);
    private static final LibraryItem FATE = item("v11", "Fate/stay night", null, "TYPE-MOON", PlayStatus.COMPLETED);
    private static final LibraryItem EVER17 = item("v17", "Ever17", null, "KID", PlayStatus.PLAYING);
    private static final List<LibraryItem> ALL = List.of(CLANNAD, FATE, EVER17);
    private static final Set<String> IN_FAVOURITES = Set.of("v4", "v11");
    private static final Set<String> WITH_DRAMA = Set.of("v4", "v17");

    private static List<String> ids(LibraryQuery query) {
        return query.apply(ALL, IN_FAVOURITES, WITH_DRAMA).stream().map(i -> i.entry().vn().id()).toList();
    }

    @Test
    void withoutFiltersEverythingInTheSameOrder() {
        assertTrue(LibraryQuery.ALL.isEmpty());
        assertEquals(List.of("v4", "v11", "v17"), ids(LibraryQuery.ALL));
    }

    @Test
    void statusAndShelfExcludeEachOther() {
        LibraryQuery playing = LibraryQuery.ALL.withStatus(PlayStatus.PLAYING);
        assertEquals(List.of("v4", "v17"), ids(playing));

        LibraryQuery favourites = playing.withShelf(FAVOURITES);
        assertNull(favourites.status(), "Elegir una estantería quita el estado");
        assertEquals(List.of("v4", "v11"), ids(favourites));

        LibraryQuery completed = favourites.withStatus(PlayStatus.COMPLETED);
        assertNull(completed.shelf(), "Y al revés");
        assertEquals(List.of("v11"), ids(completed));

        assertEquals(PlayStatus.COMPLETED, completed.withShelf(null).status(), "Quitar la estantería no toca el estado");
    }

    @Test
    void theTagCombinesWithEither() {
        assertEquals(List.of("v4", "v17"), ids(LibraryQuery.ALL.withTag(DRAMA)));
        assertEquals(List.of("v4"), ids(LibraryQuery.ALL.withTag(DRAMA).withShelf(FAVOURITES)));
        assertEquals(List.of("v4", "v17"), ids(LibraryQuery.ALL.withTag(DRAMA).withStatus(PlayStatus.PLAYING)));
        assertEquals(DRAMA, LibraryQuery.ALL.withTag(DRAMA).withStatus(PlayStatus.PLAYING).withShelf(FAVOURITES).tag());
    }

    @Test
    void theTextLooksInTitlesAndDevelopersIgnoringCase() {
        assertEquals(List.of("v11"), ids(LibraryQuery.ALL.withText("fate")));
        assertEquals(List.of("v4"), ids(LibraryQuery.ALL.withText("クラナド")), "Título original");
        assertEquals(List.of("v11"), ids(LibraryQuery.ALL.withText("type-moon")), "Desarrollador");
        assertEquals(List.of("v4", "v11", "v17"), ids(LibraryQuery.ALL.withText("   ")), "En blanco: no filtra");
        assertEquals(List.of("v17"), ids(LibraryQuery.ALL.withText("ever").withStatus(PlayStatus.PLAYING)),
                "Se combina con los demás");
    }
}

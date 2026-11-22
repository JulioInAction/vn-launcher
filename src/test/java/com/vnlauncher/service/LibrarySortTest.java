package com.vnlauncher.service;

import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.VisualNovel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibrarySortTest {

    private static LibraryItem item(String title, long addedAt, Long lastPlayed, long seconds) {
        VisualNovel vn = new VisualNovel("v-" + title, title, null, null, null, null, null, null, null, List.of());
        LibraryEntry entry = new LibraryEntry(vn, Instant.ofEpochSecond(addedAt), null, null, null, false, null,
                lastPlayed == null ? null : Instant.ofEpochSecond(lastPlayed), null);
        return new LibraryItem(entry, PlayStatus.PENDING, RouteSummary.NONE, seconds, false, false);
    }

    private static final List<LibraryItem> ITEMS = List.of(
            item("clannad", 1, 500L, 3600),
            item("Air", 3, null, 0),
            item("Kanon", 2, 900L, 60),
            item("Baldr Sky", 4, null, 7200));

    private static List<String> sorted(LibrarySort sort) {
        return ITEMS.stream().sorted(sort.comparator()).map(i -> i.entry().vn().title()).toList();
    }

    @Test
    void lastPlayedFirstAndNeverPlayedAtTheEndByTitle() {
        assertEquals(List.of("Kanon", "clannad", "Air", "Baldr Sky"), sorted(LibrarySort.LAST_PLAYED));
    }

    @Test
    void titleIgnoresCase() {
        assertEquals(List.of("Air", "Baldr Sky", "clannad", "Kanon"), sorted(LibrarySort.TITLE));
    }

    @Test
    void recentlyAddedAndPlayTime() {
        assertEquals(List.of("Baldr Sky", "Air", "Kanon", "clannad"), sorted(LibrarySort.RECENTLY_ADDED));
        assertEquals(List.of("Baldr Sky", "clannad", "Kanon", "Air"), sorted(LibrarySort.PLAY_TIME));
    }
}

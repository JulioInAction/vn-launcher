package com.vnlauncher.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import java.util.Arrays;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class PlayStatusTest {

    private static final Instant PLAYED = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void completedWhenAllRoutesAreCompleted() {
        assertEquals(PlayStatus.COMPLETED, PlayStatus.of(new RouteSummary(3, 3, 0), PLAYED));
    }

    @Test
    void playingWhenSomethingWasStartedOrLaunched() {
        assertEquals(PlayStatus.PLAYING, PlayStatus.of(new RouteSummary(3, 1, 0), null));
        assertEquals(PlayStatus.PLAYING, PlayStatus.of(new RouteSummary(3, 0, 1), null));
        assertEquals(PlayStatus.PLAYING, PlayStatus.of(RouteSummary.NONE, PLAYED), "Lanzada sin rutas marcadas");
    }

    @Test
    void pendingWhenNothingHappenedYet() {
        assertEquals(PlayStatus.PENDING, PlayStatus.of(RouteSummary.NONE, null));
        assertEquals(PlayStatus.PENDING, PlayStatus.of(new RouteSummary(2, 0, 0), null));
    }

    @Test
    void theOneSetByHandWinsUntilItIsCleared() {
        assertEquals(PlayStatus.PAUSED, PlayStatus.effective(PlayStatus.PAUSED, PlayStatus.PLAYING),
                "Aunque se siga jugando");
        assertEquals(PlayStatus.PENDING, PlayStatus.effective(PlayStatus.PENDING, PlayStatus.COMPLETED));
        assertEquals(PlayStatus.PLAYING, PlayStatus.effective(null, PlayStatus.PLAYING), "Automático");
    }

    @Test
    void pausedAndDroppedNeverComeOutOnTheirOwn() {
        for (int total = 0; total <= 2; total++) {
            for (int completed = 0; completed <= total; completed++) {
                for (Instant played : new Instant[]{null, PLAYED}) {
                    PlayStatus status = PlayStatus.of(new RouteSummary(total, completed, total - completed), played);
                    assertFalse(status.manualOnly(), status.name());
                }
            }
        }
        assertEquals(2, Arrays.stream(PlayStatus.values()).filter(PlayStatus::manualOnly).count());
    }

    @Test
    void vndbListsPlayingFinishedStalledDroppedAndNoneForPending() {
        assertEquals(OptionalInt.of(1), PlayStatus.PLAYING.vndbLabelId());
        assertEquals(OptionalInt.of(2), PlayStatus.COMPLETED.vndbLabelId());
        assertEquals(OptionalInt.of(3), PlayStatus.PAUSED.vndbLabelId());
        assertEquals(OptionalInt.of(4), PlayStatus.DROPPED.vndbLabelId());
        assertEquals(OptionalInt.empty(), PlayStatus.PENDING.vndbLabelId());
    }

    @Test
    void namesForOneAndForTheGroup() {
        assertEquals("Abandonada", PlayStatus.DROPPED.label());
        assertEquals("Abandonadas", PlayStatus.DROPPED.shelfName());
        assertEquals("En pausa", PlayStatus.PAUSED.label());
        assertEquals("En pausa", PlayStatus.PAUSED.shelfName());
    }
}

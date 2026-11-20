package com.vnlauncher.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

package com.vnlauncher.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VnDetailsTest {

    private static final VnDetails DETAILS = new VnDetails(List.of(
            new VnTag("g1", "Giro final", 2.9, 2, "cont"),
            new VnTag("g2", "Escenas sexuales", 2.8, 0, "ero"),
            new VnTag("g3", "Drama", 2.5, 0, "cont"),
            new VnTag("g4", "NVL", 2.0, 0, "tech"),
            new VnTag("g5", "Muerte de un personaje", 1.5, 1, "cont")), List.of());

    private static List<String> names(List<VnTag> tags) {
        return tags.stream().map(VnTag::name).toList();
    }

    @Test
    void byDefaultNeitherSpoilersNorSexualTags() {
        assertEquals(List.of("Drama", "NVL"), names(DETAILS.tagsToShow(false, false)));
    }

    @Test
    void showSpoilersAddsMinorAndMajorSpoilersInTheirPlace() {
        assertEquals(List.of("Giro final", "Drama", "NVL", "Muerte de un personaje"),
                names(DETAILS.tagsToShow(true, false)));
    }

    @Test
    void sexualTagsOnlyWithoutBlur() {
        assertEquals(List.of("Escenas sexuales", "Drama", "NVL"), names(DETAILS.tagsToShow(false, true)));
        assertEquals(5, DETAILS.tagsToShow(true, true).size());
    }
}

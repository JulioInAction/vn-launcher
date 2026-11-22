package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.VnDetailsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnDetails;
import com.vnlauncher.model.VnTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private static final VisualNovel VN = new VisualNovel("v1", "Fate", null, null, "2004", null, null, null, null,
            List.of());

    @TempDir
    Path tempDir;

    private VnRepository vns;
    private VnDetailsRepository details;
    private PreviewService service;

    @BeforeEach
    void setUp() throws Exception {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        vns = new VnRepository(db);
        details = new VnDetailsRepository(db);
        service = new PreviewService(vns, details, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rememberingSavesTheVnAndWhatTheSearchKnewAndCountsAsViewed() throws Exception {
        VnDetails fromSearch = new VnDetails(List.of(new VnTag("g1", "Drama", 2.0, 2, "cont")), List.of());

        service.remember(VN, fromSearch);

        assertTrue(vns.findById("v1").isPresent());
        assertEquals(java.util.Optional.of(NOW), vns.lastViewedAt("v1"));
        assertTrue(details.isFetched("v1"), "La ficha no tendrá que volver a pedirlas");
        assertEquals(List.of("g1"), details.findByVn("v1").tags().stream().map(VnTag::id).toList());
    }

    @Test
    void withoutDetailsOnlyTheVnIsSavedAndTheFichaAsksForThemItself() throws Exception {
        service.remember(VN, null);

        assertTrue(vns.findById("v1").isPresent());
        assertFalse(details.isFetched("v1"));
    }

    @Test
    void detailsAlreadySavedAreNotOverwrittenBySearchData() throws Exception {
        service.remember(VN, new VnDetails(List.of(new VnTag("g1", "Drama", 2.0, 0, "cont")), List.of()));

        service.remember(VN, new VnDetails(List.of(new VnTag("g9", "Otra", 1.0, 0, "cont")), List.of()));

        assertEquals(List.of("g1"), details.findByVn("v1").tags().stream().map(VnTag::id).toList());
    }

}

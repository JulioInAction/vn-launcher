package com.vnlauncher.service;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.dto.ImageDto;
import com.vnlauncher.api.dto.ProducerDto;
import com.vnlauncher.api.dto.RelationDto;
import com.vnlauncher.api.dto.ScreenshotDto;
import com.vnlauncher.api.dto.TagDto;
import com.vnlauncher.api.dto.VnDetailsDto;
import com.vnlauncher.api.dto.VnDto;
import com.vnlauncher.model.SearchHit;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnExtras;
import com.vnlauncher.testutil.FakeVndbServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogServiceTest {

    @Test
    void mapsDtoToModelAndCleansDescription() {
        VnDto dto = new VnDto("v4", "CLANNAD", null, "2004-04-28",
                "Texto [url=https://x]enlace[/url].",
                new ImageDto("https://t.vndb.org/cv/95/75895.jpg", 0.0, 0.5),
                List.of(new ProducerDto("p24", "Key", null)),
                null, null, null, null, null, null, null, null);

        VisualNovel vn = CatalogService.toModel(dto);

        assertEquals("v4", vn.id());
        assertEquals("Texto enlace.", vn.description());
        assertEquals("https://t.vndb.org/cv/95/75895.jpg", vn.imageUrl());
        assertEquals(0.5, vn.imageViolence());
        assertEquals("Key", vn.developerNames());
    }

    @Test
    void handlesMissingImageAndDevelopers() {
        VnDto dto = new VnDto("v1", "Sin datos", null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        VisualNovel vn = CatalogService.toModel(dto);

        assertNull(vn.imageUrl());
        assertNull(vn.imageSexual());
        assertTrue(vn.developers().isEmpty());
        assertEquals("", vn.developerNames());
    }

    @Test
    void blankSearchDoesNotCallVndb() throws Exception {
        // Cliente apuntando a un puerto donde no hay nada: si se llamara, lanzaría VndbException.
        VndbClient unreachable = new VndbClient(HttpClient.newHttpClient(),
                new RateLimiter(1, Duration.ofMinutes(5), InstantSource.system(), d -> { }),
                d -> { }, URI.create("http://127.0.0.1:1/"));
        CatalogService service = new CatalogService(unreachable);

        assertTrue(service.search("   ").isEmpty());
        assertTrue(service.search(null).isEmpty());
    }

    // ---------------------------------------------------------------- panel de la búsqueda

    private static CatalogService serviceFor(FakeVndbServer server) {
        return new CatalogService(new VndbClient(HttpClient.newHttpClient(),
                new RateLimiter(100, Duration.ofMinutes(5), InstantSource.system(), d -> { }),
                d -> { }, server.apiBase()));
    }

    @Test
    void searchIsOneRequestThatAlsoBringsThePanelData() throws Exception {
        try (FakeVndbServer server = new FakeVndbServer()) {
            server.enqueue("""
                    {"results": [{"id": "v11", "title": "Fate/hollow ataraxia", "alttitle": "フェイト／ホロウ アタラクシア",
                      "released": "2005-10-28", "description": null, "image": null, "developers": [],
                      "olang": "ja", "length_minutes": 3060, "length_votes": 256, "rating": 81.4,
                      "votecount": 5944, "languages": ["en", "ja"], "platforms": ["win", "psv"],
                      "devstatus": 0}],
                     "more": false}
                    """);

            List<SearchHit> hits = serviceFor(server).search("hollow");

            assertEquals(1, server.apiRequests().size(), "Una sola petición");
            String body = server.apiRequests().getFirst();
            for (String field : List.of("olang", "length_minutes", "length_votes", "rating", "votecount",
                    "languages", "platforms", "devstatus")) {
                assertTrue(body.contains(field), field + " en " + body);
            }
            assertFalse(body.contains("tags.") || body.contains("screenshots."), "Lo pesado va aparte: " + body);
            var stats = hits.getFirst().stats();
            assertEquals("ja", stats.originalLanguage());
            assertEquals(3060, stats.lengthMinutes());
            assertEquals(81.4, stats.rating());
            assertEquals(5944, stats.voteCount());
            assertEquals(List.of("win", "psv"), stats.platforms());
            assertEquals(0, stats.devStatus());
        }
    }

    @Test
    void extrasAreOneRequestByIdAndThenComeFromTheCache() throws Exception {
        try (FakeVndbServer server = new FakeVndbServer()) {
            server.enqueue("""
                    {"results": [{"id": "v11",
                      "tags": [{"id": "g1", "name": "Fantasía", "rating": 2.5, "spoiler": 0, "lie": false, "category": "cont"},
                               {"id": "g2", "name": "Secreto", "rating": 3.0, "spoiler": 2, "lie": false, "category": "cont"}],
                      "screenshots": [{"url": "https://t.vndb.org/sf/1.jpg", "thumbnail": "https://t.vndb.org/st/1.jpg",
                                       "sexual": 0, "violence": 0}],
                      "relations": [{"id": "v11", "title": "Fate/stay night", "relation": "orig",
                                     "relation_official": true}]}],
                     "more": false}
                    """);
            CatalogService service = serviceFor(server);
            assertTrue(service.cachedExtras("v11").isEmpty());

            VnExtras first = service.extras("v11");
            VnExtras second = service.extras("v11");

            assertEquals(1, server.apiRequests().size(), "La segunda vez sale de la caché");
            assertSame(first, second);
            assertTrue(service.cachedExtras("v11").isPresent());
            String body = server.apiRequests().getFirst();
            assertTrue(body.contains("[\"id\",\"=\",\"v11\"]"), body);
            assertTrue(body.contains("tags.spoiler") && body.contains("screenshots.thumbnail")
                    && body.contains("relations.relation"), body);
            assertEquals(List.of("Fantasía"), first.tags().stream().map(VnExtras.Tag::name).toList());
            assertEquals("https://t.vndb.org/st/1.jpg", first.screenshots().getFirst().thumbnailUrl());
            assertEquals("Fandisc de Fate/stay night", first.relation());
        }
    }

    @Test
    void extrasKeepTagsWithoutSpoilersOrLiesBestFirstAndThreeScreenshots() {
        VnDetailsDto dto = new VnDetailsDto("v11",
                List.of(new TagDto("g1", "Drama", 1.8, 0, false, "cont"),
                        new TagDto("g2", "Final triste", 2.9, 1, false, "cont"),   // spoiler leve: fuera
                        new TagDto("g3", "Mentira", 2.8, 0, true, "cont"),         // "lie": fuera
                        new TagDto("g4", "Bucle temporal", 2.7, 0, false, "cont"),
                        new TagDto("g5", "Escenas sexuales", 2.0, 0, false, "ero"),
                        new TagDto("g6", "ADV", 1.8, 0, false, "tech")),
                List.of(shot(1), shot(2), shot(3), shot(4)),
                null);

        VnExtras extras = CatalogService.toExtras(dto);

        assertEquals(List.of("Bucle temporal", "Escenas sexuales", "Drama", "ADV"),
                extras.tags().stream().map(VnExtras.Tag::name).toList(),
                "De mayor a menor; a igual puntuación, el orden de VNDB");
        assertTrue(extras.tags().get(1).isSexual(), "Las sexuales se marcan: la interfaz decide si se ven");
        assertEquals(3, extras.screenshots().size());
        assertNull(extras.relation());
    }

    private static ScreenshotDto shot(int n) {
        return new ScreenshotDto("https://t.vndb.org/sf/" + n + ".jpg", "https://t.vndb.org/st/" + n + ".jpg", 0.0, 0.0);
    }

    @Test
    void mainRelationPicksOfficialOnesFirstAndThenTheMostImportantType() {
        assertEquals("Secuela de Kanon", CatalogService.mainRelation(List.of(
                new RelationDto("v1", "Misma serie", "ser", true),
                new RelationDto("v2", "Kanon fan", "preq", false),
                new RelationDto("v3", "Kanon", "preq", true))));
        assertEquals("Fandisc de Fate/stay night", CatalogService.mainRelation(List.of(
                new RelationDto("v1", "Fate/Zero", "preq", true),
                new RelationDto("v11", "Fate/stay night", "orig", true))));
        // Fate/stay night en VNDB: una "precuela" de fans no gana a su fandisc oficial
        assertEquals("Tiene un fandisc: Fate/hollow ataraxia", CatalogService.mainRelation(List.of(
                new RelationDto("v9999", "Fate/Otome", "preq", false),
                new RelationDto("v11", "Fate/hollow ataraxia", "fan", true))));
        assertEquals("Secuela de Original", CatalogService.mainRelation(List.of(
                new RelationDto("v1", "Original", "preq", false))), "Sin oficiales, vale la no oficial");
        assertNull(CatalogService.mainRelation(List.of()));
        assertNull(CatalogService.mainRelation(null));
    }
}

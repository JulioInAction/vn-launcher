package com.vnlauncher.service;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.dto.ImageDto;
import com.vnlauncher.api.dto.ProducerDto;
import com.vnlauncher.api.dto.VnDto;
import com.vnlauncher.model.VisualNovel;
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
                List.of(new ProducerDto("p24", "Key", null)));

        VisualNovel vn = CatalogService.toModel(dto);

        assertEquals("v4", vn.id());
        assertEquals("Texto enlace.", vn.description());
        assertEquals("https://t.vndb.org/cv/95/75895.jpg", vn.imageUrl());
        assertEquals(0.5, vn.imageViolence());
        assertEquals("Key", vn.developerNames());
    }

    @Test
    void handlesMissingImageAndDevelopers() {
        VnDto dto = new VnDto("v1", "Sin datos", null, null, null, null, null);

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
}

package com.vnlauncher.service;

import com.vnlauncher.api.RateLimiter;
import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.dto.CharacterDto;
import com.vnlauncher.api.dto.CharacterVnDto;
import com.vnlauncher.api.dto.ImageDto;
import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.CharacterRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.CharacterRole;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.testutil.FakeVndbServer;
import com.vnlauncher.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CharacterServiceTest {

    @TempDir
    Path tempDir;

    private FakeVndbServer server;
    private ImageCacheService images;
    private CharacterService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeVndbServer();
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        // La VN tiene que existir en la tabla vn (FK de vn_character).
        new VnRepository(db).save(new VisualNovel("v4", "CLANNAD", null, null, null, null, null, null, null,
                List.of()), Clock.systemUTC().instant());

        HttpClient http = HttpClient.newHttpClient();
        VndbClient vndb = new VndbClient(http,
                new RateLimiter(100, Duration.ofMinutes(5), InstantSource.system(), d -> { }),
                d -> { }, server.apiBase());
        images = new ImageCacheService(http, new AppPaths(tempDir));
        service = new CharacterService(vndb, new CharacterRepository(db), images, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** Respuesta real de VNDB (4 personajes de CLANNAD) con las imágenes apuntando al servidor falso. */
    private String clannadResponse() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/com/vnlauncher/api/character-v4.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("https://t.vndb.org", server.baseUrl());
        }
    }

    @Test
    void keepsOnlyCharactersThatArePrimaryInThisVn() throws Exception {
        server.enqueue(clannadResponse());

        List<VnCharacter> characters = service.getPrimaryCharacters("v4");

        // Nagisa (primary) y Ushio (primary, spoiler). Fuera: Tomoya (main) y Akio (side en v4).
        assertEquals(List.of("c478", "c15867"),
                characters.stream().map(vc -> vc.character().id()).toList(),
                "Ordenados: primero los que no son spoiler");
        assertFalse(characters.get(0).isSpoiler());
        assertEquals(2, characters.get(1).spoilerLevel());
    }

    @Test
    void secondCallUsesTheCacheAndMakesNoRequests() throws Exception {
        server.enqueue(clannadResponse());
        service.getPrimaryCharacters("v4");
        int imagesAfterFirstCall = server.imageRequests().size();

        List<VnCharacter> again = service.getPrimaryCharacters("v4");

        assertEquals(2, again.size());
        assertEquals(1, server.apiRequests().size(), "La API solo se consulta la primera vez");
        assertEquals(imagesAfterFirstCall, server.imageRequests().size(), "Las imágenes tampoco se repiten");
    }

    @Test
    void downloadsCharacterImages() throws Exception {
        server.enqueue(clannadResponse());

        List<VnCharacter> characters = service.getPrimaryCharacters("v4");

        for (VnCharacter vc : characters) {
            String path = vc.character().imagePath();
            assertNotNull(path, "Imagen de " + vc.character().name());
            assertTrue(path.startsWith("images/char/" + vc.character().id()));
            assertTrue(Files.exists(images.resolve(path)));
        }
    }

    @Test
    void refreshAsksVndbAgainEvenIfAlreadyCached() throws Exception {
        server.enqueue(clannadResponse());
        server.enqueue("{\"results\": [], \"more\": false}");
        service.getPrimaryCharacters("v4");

        List<VnCharacter> refreshed = service.refresh("v4");

        assertEquals(2, server.apiRequests().size(), "Actualizar sí vuelve a preguntar a VNDB");
        assertTrue(refreshed.isEmpty(), "Y guarda lo que VNDB tenga ahora");
    }

    @Test
    void emptyResultIsCachedToo() throws Exception {
        server.enqueue("{\"results\": [], \"more\": false}");

        assertTrue(service.getPrimaryCharacters("v4").isEmpty());
        assertTrue(service.getPrimaryCharacters("v4").isEmpty());
        assertEquals(1, server.apiRequests().size(), "Una VN sin personajes tampoco se vuelve a pedir");
    }

    // ---- toModel: casos que la respuesta real no cubre ----

    private static CharacterDto dto(List<CharacterVnDto> vns) {
        return new CharacterDto("c1", "Nombre", null, "[b]Hola[/b]", new ImageDto("u", 0.0, 0.0), vns);
    }

    @Test
    void toModelMergesRepeatedAppearancesOfTheSameVn() {
        // La misma VN listada dos veces (dos releases) con distinto rol y spoiler
        Optional<VnCharacter> vc = CharacterService.toModel(dto(List.of(
                new CharacterVnDto("v4", "side", 2),
                new CharacterVnDto("v4", "primary", 0),
                new CharacterVnDto("v99", "main", 0))), "v4");

        assertTrue(vc.isPresent());
        assertEquals(CharacterRole.PRIMARY, vc.get().role(), "Gana el rol más importante de ESTA VN");
        assertEquals(0, vc.get().spoilerLevel(), "Gana el spoiler más bajo");
        assertEquals("Hola", vc.get().character().description(), "La descripción se limpia");
    }

    @Test
    void toModelIsEmptyIfCharacterIsNotInThatVn() {
        assertTrue(CharacterService.toModel(dto(List.of(new CharacterVnDto("v99", "primary", 0))), "v4")
                .isEmpty());
    }
}

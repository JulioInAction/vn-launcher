package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.CharacterRole;
import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CharacterRepositoryTest {

    @TempDir
    Path tempDir;

    private CharacterRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        VnRepository vns = new VnRepository(db);
        for (String id : List.of("v1", "v2")) {
            vns.save(new VisualNovel(id, "VN " + id, null, null, null, null, null, null, null, List.of()),
                    Instant.EPOCH);
        }
        repo = new CharacterRepository(db);
    }

    private static VnCharacter vc(String id, String name, String imageUrl, CharacterRole role, int spoiler) {
        return new VnCharacter(new GameCharacter(id, name, null, "desc", imageUrl, null, 0.5, null), role, spoiler);
    }

    @Test
    void notFetchedUntilSaved() throws SQLException {
        assertFalse(repo.isFetched("v1"));

        repo.saveForVn("v1", List.of(), Instant.EPOCH);

        assertTrue(repo.isFetched("v1"), "Incluso con 0 personajes queda marcada");
        assertFalse(repo.isFetched("v2"));
    }

    @Test
    void savesAndReadsSpoilersLastThenByName() throws SQLException {
        repo.saveForVn("v1", List.of(
                vc("c1", "Zoe", null, CharacterRole.PRIMARY, 0),
                vc("c2", "ana", null, CharacterRole.PRIMARY, 0),
                vc("c3", "Beatriz", null, CharacterRole.PRIMARY, 2)), Instant.EPOCH);

        List<VnCharacter> found = repo.findByVn("v1");

        assertEquals(List.of("ana", "Zoe", "Beatriz"), found.stream().map(v -> v.character().name()).toList());
        assertEquals(CharacterRole.PRIMARY, found.getFirst().role());
        assertEquals(0.5, found.getFirst().character().imageSexual());
        assertNull(found.getFirst().character().imageViolence());
    }

    @Test
    void sameCharacterCanHaveDifferentRolesInDifferentVns() throws SQLException {
        repo.saveForVn("v1", List.of(vc("c1", "Ana", null, CharacterRole.PRIMARY, 0)), Instant.EPOCH);
        repo.saveForVn("v2", List.of(vc("c1", "Ana", null, CharacterRole.SIDE, 1)), Instant.EPOCH);

        assertEquals(CharacterRole.PRIMARY, repo.findByVn("v1").getFirst().role());
        assertEquals(CharacterRole.SIDE, repo.findByVn("v2").getFirst().role());
        assertEquals(1, repo.findByVn("v2").getFirst().spoilerLevel());
    }

    @Test
    void savingAgainReplacesTheVnCharactersButKeepsImagePath() throws SQLException {
        repo.saveForVn("v1", List.of(vc("c1", "Ana", "https://img/1.jpg", CharacterRole.PRIMARY, 0),
                vc("c2", "Bea", null, CharacterRole.PRIMARY, 0)), Instant.EPOCH);
        repo.updateImagePath("c1", "images/char/c1.jpg");

        repo.saveForVn("v1", List.of(vc("c1", "Ana", "https://img/1.jpg", CharacterRole.PRIMARY, 0)), Instant.EPOCH);

        List<VnCharacter> found = repo.findByVn("v1");
        assertEquals(1, found.size(), "Bea ya no está en esta VN");
        assertEquals("images/char/c1.jpg", found.getFirst().character().imagePath());
    }
}

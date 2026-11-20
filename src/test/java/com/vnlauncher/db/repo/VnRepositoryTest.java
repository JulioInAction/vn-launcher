package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.Developer;
import com.vnlauncher.model.VisualNovel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VnRepositoryTest {

    @TempDir
    Path tempDir;

    private VnRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        repo = new VnRepository(db);
    }

    /** VN de ejemplo; se puede cambiar la URL de la portada para algunos tests. */
    static VisualNovel clannad(String imageUrl, List<Developer> devs) {
        return new VisualNovel("v4", "CLANNAD", "クラナド", "Descripción", "2004-04-28",
                imageUrl, null, 0.0, null, devs);
    }

    @Test
    void savesAndReadsBackAllFields() throws SQLException {
        repo.save(clannad("https://t.vndb.org/cv/95/75895.jpg",
                List.of(new Developer("p24", "Key", null))), Instant.EPOCH);

        VisualNovel vn = repo.findById("v4").orElseThrow();

        assertEquals("CLANNAD", vn.title());
        assertEquals("クラナド", vn.altTitle());
        assertEquals(0.0, vn.imageSexual());
        assertNull(vn.imageViolence(), "NULL en la BD debe volver como null, no como 0.0");
        assertEquals(List.of(new Developer("p24", "Key", null)), vn.developers());
    }

    @Test
    void findByIdReturnsEmptyForUnknownVn() throws SQLException {
        assertTrue(repo.findById("v999").isEmpty());
    }

    @Test
    void savingAgainUpdatesDataAndReplacesDevelopers() throws SQLException {
        repo.save(clannad("u1", List.of(new Developer("p24", "Key", null))), Instant.EPOCH);
        repo.save(clannad("u1", List.of(new Developer("p1", "Visual Arts", null))), Instant.EPOCH);

        assertEquals(List.of(new Developer("p1", "Visual Arts", null)),
                repo.findById("v4").orElseThrow().developers());
    }

    @Test
    void keepsImagePathIfCoverUrlDidNotChange() throws SQLException {
        repo.save(clannad("https://img/1.jpg", List.of()), Instant.EPOCH);
        repo.updateImagePath("v4", "images/vn/v4.jpg");

        repo.save(clannad("https://img/1.jpg", List.of()), Instant.EPOCH);

        assertEquals("images/vn/v4.jpg", repo.findById("v4").orElseThrow().imagePath());
    }

    @Test
    void resetsImagePathIfCoverUrlChanged() throws SQLException {
        repo.save(clannad("https://img/1.jpg", List.of()), Instant.EPOCH);
        repo.updateImagePath("v4", "images/vn/v4.jpg");

        repo.save(clannad("https://img/2.jpg", List.of()), Instant.EPOCH);

        assertNull(repo.findById("v4").orElseThrow().imagePath(), "La portada nueva hay que descargarla");
    }
}

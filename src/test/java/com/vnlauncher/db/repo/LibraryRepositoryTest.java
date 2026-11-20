package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.Developer;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryRepositoryTest {

    @TempDir
    Path tempDir;

    private VnRepository vns;
    private LibraryRepository library;

    @BeforeEach
    void setUp() throws SQLException {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        vns = new VnRepository(db);
        library = new LibraryRepository(db);
    }

    private void saveVn(String id, String title, Developer... devs) throws SQLException {
        vns.save(new VisualNovel(id, title, null, null, null, null, null, null, null, List.of(devs)),
                Instant.EPOCH);
    }

    @Test
    void addIsIdempotentAndKeepsOriginalDate() throws SQLException {
        saveVn("v4", "CLANNAD");
        library.add("v4", Instant.ofEpochSecond(1000));
        library.add("v4", Instant.ofEpochSecond(2000)); // no debe fallar ni cambiar la fecha

        List<LibraryEntry> all = library.findAll();
        assertEquals(1, all.size());
        assertEquals(Instant.ofEpochSecond(1000), all.getFirst().addedAt());
    }

    @Test
    void findAllIsSortedByTitleIgnoringCaseAndLoadsDevelopers() throws SQLException {
        saveVn("v1", "kanon", new Developer("p24", "Key", null));
        saveVn("v2", "AIR", new Developer("p24", "Key", null));
        saveVn("v3", "Fate/stay night", new Developer("p23", "TYPE-MOON", null));
        saveVn("v4", "No está en la biblioteca");
        library.add("v1", Instant.EPOCH);
        library.add("v2", Instant.EPOCH);
        library.add("v3", Instant.EPOCH);

        List<LibraryEntry> all = library.findAll();

        assertEquals(List.of("AIR", "Fate/stay night", "kanon"),
                all.stream().map(e -> e.vn().title()).toList());
        assertEquals("TYPE-MOON", all.get(1).vn().developerNames());
    }

    @Test
    void containsAndRemove() throws SQLException {
        saveVn("v4", "CLANNAD");
        assertFalse(library.contains("v4"));

        library.add("v4", Instant.EPOCH);
        assertTrue(library.contains("v4"));
        assertTrue(library.findByVnId("v4").isPresent());

        library.remove("v4");
        assertFalse(library.contains("v4"));
        assertTrue(library.findByVnId("v4").isEmpty());
        assertTrue(vns.findById("v4").isPresent(), "La caché de VNDB se conserva");
    }
}

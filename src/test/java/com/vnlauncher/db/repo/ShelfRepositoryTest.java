package com.vnlauncher.db.repo;

import com.vnlauncher.db.Database;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.ShelfColor;
import com.vnlauncher.model.VisualNovel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShelfRepositoryTest {

    @TempDir
    Path tempDir;

    private ShelfRepository shelves;
    private LibraryRepository library;

    @BeforeEach
    void setUp() throws SQLException {
        Database db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        shelves = new ShelfRepository(db);
        library = new LibraryRepository(db);
        VnRepository vns = new VnRepository(db);
        for (String id : List.of("v4", "v11", "v17")) {
            vns.save(new VisualNovel(id, "VN " + id, null, null, null, null, null, null, null, List.of()), Instant.EPOCH);
            library.add(id, Instant.EPOCH);
        }
    }

    private List<String> names() throws SQLException {
        return shelves.findAll().stream().map(Shelf::name).toList();
    }

    @Test
    void createdAtTheEndAndRenamed() throws SQLException {
        Shelf favourites = shelves.create("Favoritas", ShelfColor.PINK, Instant.EPOCH);
        Shelf deck = shelves.create("Para la Steam Deck", ShelfColor.BLUE, Instant.EPOCH);
        assertEquals(0, favourites.position());
        assertEquals(1, deck.position());

        shelves.update(deck.id(), "Steam Deck", ShelfColor.GREEN);

        Shelf renamed = shelves.find(deck.id()).orElseThrow();
        assertEquals("Steam Deck", renamed.name());
        assertEquals(ShelfColor.GREEN, renamed.color());
    }

    @Test
    void movedUpAndDownWithoutGaps() throws SQLException {
        long a = shelves.create("A", ShelfColor.PINK, Instant.EPOCH).id();
        shelves.create("B", ShelfColor.BLUE, Instant.EPOCH);
        long c = shelves.create("C", ShelfColor.ORANGE, Instant.EPOCH).id();

        shelves.moveTo(c, 0);
        assertEquals(List.of("C", "A", "B"), names(), "Subir del todo");
        shelves.moveTo(a, 2);
        assertEquals(List.of("C", "B", "A"), names(), "Bajar");
        shelves.moveTo(a, 99);
        assertEquals(List.of("C", "B", "A"), names(), "Más allá del final se queda al final");
        assertEquals(List.of(0, 1, 2), shelves.findAll().stream().map(Shelf::position).toList());
    }

    @Test
    void novelsInAndOutWithTheirCounts() throws SQLException {
        Shelf favourites = shelves.create("Favoritas", ShelfColor.PINK, Instant.EPOCH);
        Shelf deck = shelves.create("Para la Steam Deck", ShelfColor.BLUE, Instant.EPOCH);
        shelves.addVn(favourites.id(), "v4", Instant.EPOCH);
        shelves.addVn(favourites.id(), "v4", Instant.EPOCH); // dos veces: no pasa nada
        shelves.addVn(favourites.id(), "v11", Instant.EPOCH);
        shelves.addVn(deck.id(), "v4", Instant.EPOCH);

        assertEquals(List.of(2, 1), shelves.findAll().stream().map(Shelf::novels).toList());
        assertEquals(Set.of("v4", "v11"), shelves.vnIdsIn(favourites.id()));
        assertEquals(List.of("Favoritas", "Para la Steam Deck"),
                shelves.findByVn("v4").stream().map(Shelf::name).toList(), "Las de la ficha, en orden");
        assertEquals(List.of(), shelves.findByVn("v17"));

        shelves.removeVn(favourites.id(), "v4");
        assertEquals(Set.of("v11"), shelves.vnIdsIn(favourites.id()));
    }

    @Test
    void deletingAShelfKeepsItsNovels() throws SQLException {
        Shelf first = shelves.create("Primera", ShelfColor.PINK, Instant.EPOCH);
        shelves.create("Segunda", ShelfColor.BLUE, Instant.EPOCH);
        shelves.addVn(first.id(), "v4", Instant.EPOCH);

        shelves.delete(first.id());

        assertEquals(List.of("Segunda"), names());
        assertEquals(0, shelves.findAll().getFirst().position(), "Sin huecos");
        assertTrue(library.contains("v4"), "La VN sigue en la biblioteca");
    }

    @Test
    void removingANovelFromTheLibraryTakesItOutOfItsShelves() throws SQLException {
        Shelf favourites = shelves.create("Favoritas", ShelfColor.PINK, Instant.EPOCH);
        shelves.addVn(favourites.id(), "v4", Instant.EPOCH);

        library.remove("v4");

        assertEquals(Set.of(), shelves.vnIdsIn(favourites.id()));
        assertEquals(0, shelves.find(favourites.id()).orElseThrow().novels());
    }

    @Test
    void theDatabaseAlsoRejectsRepeatedNames() throws SQLException {
        shelves.create("Favoritas", ShelfColor.PINK, Instant.EPOCH);
        assertThrows(SQLException.class, () -> shelves.create("FAVORITAS", ShelfColor.BLUE, Instant.EPOCH),
                "Por si algo se salta ShelfService");
    }
}

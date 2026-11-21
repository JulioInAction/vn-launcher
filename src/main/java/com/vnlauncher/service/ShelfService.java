package com.vnlauncher.service;

import com.vnlauncher.db.repo.ShelfRepository;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.ShelfColor;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Estanterías del usuario: crearlas, cambiarlas, ordenarlas, borrarlas y meter o sacar VNs.
 *
 * <p>Las reglas del nombre ({@link #nameProblem}) son una función pura: el diálogo la usa mientras
 * se escribe (para avisar y desactivar «Guardar») y create/update la repiten por si acaso.
 */
public class ShelfService {

    /** Largo máximo del nombre (el diálogo no deja escribir más). */
    public static final int MAX_NAME = 40;

    static final String EMPTY = "Ponle un nombre.";
    /** «Como mucho 40 caracteres.» */
    static final String TOO_LONG = "Como mucho %d caracteres.";
    static final String DUPLICATE = "Ya tienes una estantería con ese nombre.";

    private final ShelfRepository repository;
    private final Clock clock;

    public ShelfService(ShelfRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Qué le pasa a un nombre, o vacío si vale: no puede estar en blanco, tener más de
     * {@link #MAX_NAME} caracteres ni repetir el de otra estantería (sin distinguir mayúsculas,
     * tampoco en letras con acento: «FAVORITAS» y «Favoritas» son la misma).
     *
     * @param existing las estanterías que hay
     * @param editing  la que se está editando (su propio nombre no cuenta como repetido), o null
     */
    public static Optional<String> nameProblem(String name, List<Shelf> existing, Shelf editing) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            return Optional.of(EMPTY);
        }
        if (trimmed.length() > MAX_NAME) {
            return Optional.of(String.format(TOO_LONG, MAX_NAME));
        }
        boolean taken = existing.stream()
                .filter(shelf -> editing == null || shelf.id() != editing.id())
                .anyMatch(shelf -> shelf.name().equalsIgnoreCase(trimmed));
        return taken ? Optional.of(DUPLICATE) : Optional.empty();
    }

    public List<Shelf> findAll() throws SQLException {
        return repository.findAll();
    }

    /** Las estanterías de una VN, en el orden de la barra lateral. */
    public List<Shelf> shelvesOf(String vnId) throws SQLException {
        return repository.findByVn(vnId);
    }

    /** Las VNs de una estantería (para filtrar la biblioteca). */
    public Set<String> vnIdsIn(long shelfId) throws SQLException {
        return repository.vnIdsIn(shelfId);
    }

    /** @throws IllegalArgumentException si el nombre no vale (el diálogo ya lo comprueba antes) */
    public Shelf create(String name, ShelfColor color) throws SQLException {
        check(name, null);
        return repository.create(name.strip(), color, clock.instant());
    }

    /** Cambia nombre y color. */
    public void update(Shelf shelf, String name, ShelfColor color) throws SQLException {
        check(name, shelf);
        repository.update(shelf.id(), name.strip(), color);
    }

    /** La pone en otro sitio de la lista (0 = arriba del todo). */
    public void moveTo(long shelfId, int index) throws SQLException {
        repository.moveTo(shelfId, index);
    }

    /** Borra la estantería; sus VNs siguen en la biblioteca. */
    public void delete(long shelfId) throws SQLException {
        repository.delete(shelfId);
    }

    public void addVn(long shelfId, String vnId) throws SQLException {
        repository.addVn(shelfId, vnId, clock.instant());
    }

    public void removeVn(long shelfId, String vnId) throws SQLException {
        repository.removeVn(shelfId, vnId);
    }

    private void check(String name, Shelf editing) throws SQLException {
        Optional<String> problem = nameProblem(name, repository.findAll(), editing);
        if (problem.isPresent()) {
            throw new IllegalArgumentException(problem.get());
        }
    }
}

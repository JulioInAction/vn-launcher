package com.vnlauncher.service;

import com.vnlauncher.db.Database;
import com.vnlauncher.db.repo.CharacterRepository;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.CharacterRole;
import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteServiceTest {

    private static final Instant T1 = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-21T10:00:00Z");

    @TempDir
    Path tempDir;

    private Database db;
    private RouteService service;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db"));
        db.migrate();
        new VnRepository(db).save(new VisualNovel("v4", "CLANNAD", null, null, null, null, null, null, null,
                List.of()), T1);
        new LibraryRepository(db).add("v4", T1);
        new CharacterRepository(db).saveForVn("v4", List.of(new VnCharacter(
                new GameCharacter("c478", "Furukawa Nagisa", null, null, null, null, null, null),
                CharacterRole.PRIMARY, 0)), T1);
        service = serviceAt(T1);
    }

    /** Servicio con el reloj parado en un instante concreto. */
    private RouteService serviceAt(Instant now) {
        return new RouteService(new RouteRepository(db), Clock.fixed(now, ZoneOffset.UTC));
    }

    private List<String> names() throws SQLException {
        return service.routesOf("v4").stream().map(Route::name).toList();
    }

    @Test
    void newRoutesGoToTheEndAsPending() throws SQLException {
        Route nagisa = service.addRoute("v4", "c478", "  Nagisa  ", "  ");
        Route after = service.addRoute("v4", null, "After Story", "Desbloquear con todas las luces");

        assertNotNull(nagisa.id());
        assertEquals("Nagisa", nagisa.name(), "Se quitan los espacios de los extremos");
        assertNull(nagisa.notes(), "Notas en blanco se guardan como null");
        assertEquals(1, nagisa.recommendedOrder());
        assertEquals(2, after.recommendedOrder());
        assertEquals(RouteStatus.PENDING, after.status());
        assertNull(after.characterId(), "Ruta sin heroína");
    }

    @Test
    void nameIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> service.addRoute("v4", null, "   ", null));
    }

    @Test
    void completingSetsDateAndUncompletingClearsIt() throws SQLException {
        long id = service.addRoute("v4", "c478", "Nagisa", null).id();

        Route completed = service.setStatus(id, RouteStatus.COMPLETED);
        assertEquals(T1, completed.completedAt());

        // Editarla otro día sin cambiar el estado conserva la fecha original
        Route edited = serviceAt(T2).updateRoute(new Route(id, "v4", "c478", "Nagisa (renombrada)", 0,
                RouteStatus.COMPLETED, "notas", null));
        assertEquals(T1, edited.completedAt());
        assertEquals("Nagisa (renombrada)", service.routesOf("v4").getFirst().name());

        Route reopened = service.setStatus(id, RouteStatus.IN_PROGRESS);
        assertNull(reopened.completedAt());
    }

    @Test
    void moveUpAndDownSwapNeighboursAndIgnoreEdges() throws SQLException {
        long a = service.addRoute("v4", null, "A", null).id();
        service.addRoute("v4", null, "B", null);
        long c = service.addRoute("v4", null, "C", null).id();

        service.moveUp(c);
        assertEquals(List.of("A", "C", "B"), names());

        service.moveDown(a);
        assertEquals(List.of("C", "A", "B"), names());

        service.moveUp(c); // ya es la primera
        assertEquals(List.of("C", "A", "B"), names());

        assertEquals(List.of(1, 2, 3), service.routesOf("v4").stream().map(Route::recommendedOrder).toList());
    }

    @Test
    void moveToPlacesRouteAtAnyPosition() throws SQLException {
        service.addRoute("v4", null, "A", null);
        service.addRoute("v4", null, "B", null);
        long c = service.addRoute("v4", null, "C", null).id();
        long a = service.routesOf("v4").getFirst().id();

        service.moveTo(c, 0);
        assertEquals(List.of("C", "A", "B"), names());

        service.moveTo(a, 99); // fuera de rango: se queda al final
        assertEquals(List.of("C", "B", "A"), names());
        assertEquals(List.of(1, 2, 3), service.routesOf("v4").stream().map(Route::recommendedOrder).toList());
    }

    @Test
    void deletingRenumbersTheRest() throws SQLException {
        service.addRoute("v4", null, "A", null);
        long b = service.addRoute("v4", null, "B", null).id();
        service.addRoute("v4", null, "C", null);

        service.delete(b);

        List<Route> routes = service.routesOf("v4");
        assertEquals(List.of("A", "C"), routes.stream().map(Route::name).toList());
        assertEquals(List.of(1, 2), routes.stream().map(Route::recommendedOrder).toList(), "Sin huecos");
    }

    @Test
    void progressCountsCompletedRoutes() throws SQLException {
        long a = service.addRoute("v4", null, "A", null).id();
        service.addRoute("v4", null, "B", null);
        service.addRoute("v4", null, "C", null);
        service.setStatus(a, RouteStatus.COMPLETED);

        var progress = service.progressOf(service.routesOf("v4"));

        assertEquals("1/3", progress.toString());
        assertEquals(1.0 / 3, progress.fraction(), 1e-9);
        assertEquals(0, service.progressOf(List.of()).fraction(), "Sin rutas no se divide entre 0");
    }

    @Test
    void invalidStatusIsRejectedByTheDatabase() throws SQLException {
        // Defensa en profundidad: aunque Java solo deja usar el enum, el CHECK de la tabla también protege.
        try (var conn = db.getConnection(); var st = conn.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO route (vn_id, name, recommended_order, status) VALUES ('v4', 'X', 1, 'DONE')"));
        }
    }
}

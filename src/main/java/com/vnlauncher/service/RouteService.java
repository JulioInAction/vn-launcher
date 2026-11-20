package com.vnlauncher.service;

import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteProgress;
import com.vnlauncher.model.RouteStatus;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reglas de negocio de las rutas:
 * <ul>
 *   <li>el nombre es obligatorio;</li>
 *   <li>el orden recomendado siempre es 1, 2, 3... sin huecos: una ruta nueva va al final,
 *       y al subir/bajar/borrar se renumeran todas;</li>
 *   <li>completed_at se rellena al pasar a "Completada" y se borra si deja de estarlo.</li>
 * </ul>
 */
public class RouteService {

    private final RouteRepository repository;
    private final Clock clock;

    public RouteService(RouteRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<Route> routesOf(String vnId) throws SQLException {
        return repository.findByVn(vnId);
    }

    public RouteProgress progressOf(List<Route> routes) {
        int completed = (int) routes.stream().filter(Route::isCompleted).count();
        return new RouteProgress(completed, routes.size());
    }

    /**
     * Crea una ruta al final del orden recomendado, en estado Pendiente.
     *
     * @param characterId null para rutas sin heroína
     */
    public Route addRoute(String vnId, String characterId, String name, String notes) throws SQLException {
        int nextOrder = repository.findByVn(vnId).size() + 1;
        Route route = new Route(null, vnId, characterId, cleanName(name), nextOrder,
                RouteStatus.PENDING, cleanNotes(notes), null);
        return repository.insert(route);
    }

    /** Guarda los cambios hechos en el diálogo de edición (nombre, heroína, estado, notas). */
    public Route updateRoute(Route edited) throws SQLException {
        Route current = repository.findById(edited.id())
                .orElseThrow(() -> new IllegalArgumentException("No existe la ruta " + edited.id()));
        Route toSave = new Route(current.id(), current.vnId(), edited.characterId(), cleanName(edited.name()),
                current.recommendedOrder(), edited.status(), cleanNotes(edited.notes()),
                completedAtFor(current, edited.status()));
        repository.update(toSave);
        return toSave;
    }

    /** Cambia solo el estado (desde el desplegable de la lista). */
    public Route setStatus(long routeId, RouteStatus status) throws SQLException {
        Route current = repository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la ruta " + routeId));
        Route updated = current.withStatus(status, completedAtFor(current, status));
        repository.update(updated);
        return updated;
    }

    public void delete(long routeId) throws SQLException {
        Route route = repository.findById(routeId).orElse(null);
        if (route == null) {
            return;
        }
        repository.delete(routeId);
        renumber(repository.findByVn(route.vnId())); // cerrar el hueco que deja
    }

    public void moveUp(long routeId) throws SQLException {
        move(routeId, -1);
    }

    public void moveDown(long routeId) throws SQLException {
        move(routeId, +1);
    }

    /**
     * Mueve la ruta a la posición {@code newIndex} (0 = la primera) y renumera el resto.
     * Es lo que usa arrastrar y soltar en la interfaz.
     */
    public void moveTo(long routeId, int newIndex) throws SQLException {
        Route route = repository.findById(routeId).orElse(null);
        if (route == null) {
            return;
        }
        List<Route> routes = new ArrayList<>(repository.findByVn(route.vnId()));
        Route moving = routes.remove(indexOf(routes, routeId));
        // Math.clamp (Java 21): limita el valor al rango [0, tamaño] por si llega fuera de rango.
        routes.add(Math.clamp(newIndex, 0, routes.size()), moving);
        renumber(routes);
    }

    /** Intercambia la ruta con su vecina (anterior si delta=-1, siguiente si +1) y renumera. */
    private void move(long routeId, int delta) throws SQLException {
        Route route = repository.findById(routeId).orElse(null);
        if (route == null) {
            return;
        }
        List<Route> routes = new ArrayList<>(repository.findByVn(route.vnId()));
        int index = indexOf(routes, routeId);
        int target = index + delta;
        if (target < 0 || target >= routes.size()) {
            return; // ya es la primera/última: no hay a dónde moverla
        }
        Collections.swap(routes, index, target);
        renumber(routes);
    }

    /** Asigna 1, 2, 3... según la posición en la lista, y guarda solo las que cambian. */
    private void renumber(List<Route> routes) throws SQLException {
        List<Route> changed = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            if (r.recommendedOrder() != i + 1) {
                changed.add(r.withOrder(i + 1));
            }
        }
        if (!changed.isEmpty()) {
            repository.updateOrders(changed);
        }
    }

    private static int indexOf(List<Route> routes, long id) {
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).id() == id) {
                return i;
            }
        }
        throw new IllegalStateException("Ruta " + id + " no encontrada en su VN");
    }

    /**
     * Fecha de completado para el nuevo estado: si ya estaba completada se conserva la fecha
     * original; si se completa ahora, la hora actual; si no está completada, null.
     */
    private Instant completedAtFor(Route current, RouteStatus newStatus) {
        if (newStatus != RouteStatus.COMPLETED) {
            return null;
        }
        return current.isCompleted() && current.completedAt() != null ? current.completedAt() : clock.instant();
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("La ruta necesita un nombre");
        }
        return name.strip();
    }

    /** Notas vacías se guardan como NULL, no como "". */
    private static String cleanNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.strip();
    }
}

package com.vnlauncher.service;

import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.PlaySessionRepository;
import com.vnlauncher.db.repo.RouteRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.CurrentRoute;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.RouteSummary;
import com.vnlauncher.model.VisualNovel;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Lógica de la biblioteca: añadir/quitar VNs y mantener sus portadas descargadas.
 *
 * <p>Fíjate en que añadir una VN NO hace ninguna petición a la API de VNDB: los datos
 * ya vienen completos del resultado de búsqueda. Solo se descarga la portada (servidor de imágenes).
 */
public class LibraryService {

    private static final System.Logger LOG = System.getLogger(LibraryService.class.getName());

    private final VnRepository vnRepository;
    private final LibraryRepository libraryRepository;
    private final RouteRepository routeRepository;
    private final PlaySessionRepository sessionRepository;
    private final ImageCacheService imageCache;
    private final Clock clock; // inyectado para que los tests puedan fijar la hora

    public LibraryService(VnRepository vnRepository, LibraryRepository libraryRepository,
                          RouteRepository routeRepository, PlaySessionRepository sessionRepository,
                          ImageCacheService imageCache, Clock clock) {
        this.vnRepository = vnRepository;
        this.libraryRepository = libraryRepository;
        this.routeRepository = routeRepository;
        this.sessionRepository = sessionRepository;
        this.imageCache = imageCache;
        this.clock = clock;
    }

    /**
     * Guarda la VN en la caché local, la añade a la biblioteca y descarga su portada.
     * Si la descarga falla (p. ej. sin conexión), la VN queda añadida igualmente y
     * la portada se reintentará con {@link #downloadMissingCovers()}.
     */
    public void addToLibrary(VisualNovel vn) throws SQLException, InterruptedException {
        Instant now = clock.instant();
        // Son dos operaciones separadas: si fallara la segunda, la VN quedaría en la caché pero
        // no en la biblioteca, que es inofensivo (basta con volver a pulsar "Añadir").
        vnRepository.save(vn, now);
        libraryRepository.add(vn.id(), now);
        // Volvemos a leerla para tener el image_path actual (puede que ya estuviera descargada).
        Optional<VisualNovel> saved = vnRepository.findById(vn.id());
        if (saved.isPresent()) {
            ensureCover(saved.get());
        }
    }

    /**
     * Guarda los datos nuevos de una VN que ya está en la biblioteca ("Actualizar desde VNDB") y
     * descarga la portada si ha cambiado. VnRepository.save deja image_path a NULL cuando cambia la
     * URL de la portada, así que ensureCover la ve como "falta" y la vuelve a descargar.
     */
    public void updateFromVndb(VisualNovel fresh) throws SQLException, InterruptedException {
        vnRepository.save(fresh, clock.instant());
        Optional<VisualNovel> saved = vnRepository.findById(fresh.id());
        if (saved.isPresent()) {
            ensureCover(saved.get());
        }
    }

    /** Cuándo se pidieron a VNDB los datos de la VN. */
    public Optional<Instant> fetchedAt(String vnId) throws SQLException {
        return vnRepository.fetchedAt(vnId);
    }

    /**
     * Quita la VN de la biblioteca: sus rutas, sesiones y estanterías se van con ella (ON DELETE
     * CASCADE) y también se borra su portada propia de covers/, que ya no usaría nadie. Los datos de
     * VNDB y sus imágenes de la caché se quedan (si se vuelve a añadir, no hay que pedirlos otra vez).
     *
     * <p>Si después se restaura una copia antigua en la que estaba, vuelve con la portada de VNDB: la
     * app ya tolera que falte el archivo de la elegida.
     */
    public void remove(String vnId) throws SQLException {
        Optional<String> customCover = libraryRepository.findByVnId(vnId).map(LibraryEntry::coverOverride);
        libraryRepository.remove(vnId);
        customCover.flatMap(relative -> CoverService.customCoverFile(imageCache::resolve, relative))
                .ifPresent(LibraryService::deleteQuietly);
    }

    /** La VN ya está quitada: si el archivo no se puede borrar, solo se apunta en el registro. */
    private static void deleteQuietly(java.nio.file.Path file) {
        try {
            java.nio.file.Files.deleteIfExists(file);
            LOG.log(System.Logger.Level.INFO, "Portada propia borrada: {0}", file.getFileName());
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo borrar la portada " + file, e);
        }
    }

    public boolean isInLibrary(String vnId) throws SQLException {
        return libraryRepository.contains(vnId);
    }

    public List<LibraryEntry> findAll() throws SQLException {
        return libraryRepository.findAll();
    }

    /**
     * Fija a mano el estado de una VN (selector «Estado» de la ficha). Se queda aunque se juegue,
     * hasta que se vuelva a automático.
     *
     * @param status el estado, o null para «Automático» (calculado con las rutas y partidas)
     */
    public void setStatus(String vnId, PlayStatus status) throws SQLException {
        libraryRepository.updateStatusOverride(vnId, status);
    }

    /** Toda la biblioteca con el estado de cada VN (el fijado a mano, o el calculado). */
    public List<LibraryItem> findAllWithStatus() throws SQLException {
        // Una consulta para las rutas de todas las VNs y otra para los tiempos (no una por VN)
        Map<String, RouteSummary> routes = routeRepository.summariesByVn();
        Map<String, Long> seconds = sessionRepository.totalSecondsByVn();
        return findAll().stream()
                .map(e -> {
                    RouteSummary summary = routes.getOrDefault(e.vn().id(), RouteSummary.NONE);
                    return new LibraryItem(e, PlayStatus.of(summary, e.lastPlayedAt()), summary,
                            seconds.getOrDefault(e.vn().id(), 0L),
                            GameFiles.of(e).exeMissing()); // mira el disco: esto va en segundo plano
                })
                .toList();
    }

    /** Las {@code limit} VNs jugadas más recientemente (para "Recientes" de la barra lateral). */
    public static List<LibraryEntry> recentlyPlayed(List<LibraryItem> items, int limit) {
        return items.stream()
                .map(LibraryItem::entry)
                .filter(e -> e.lastPlayedAt() != null)
                // Comparator.comparing(...).reversed(): de la más reciente a la más antigua
                .sorted(Comparator.comparing(LibraryEntry::lastPlayedAt).reversed())
                .limit(limit)
                .toList();
    }

    /** Segundos jugados a una VN (suma de sus sesiones terminadas). */
    public long totalPlaySeconds(String vnId) throws SQLException {
        return sessionRepository.totalSeconds(vnId);
    }

    /** Ruta en curso, para la franja "Continuar". */
    public Optional<CurrentRoute> currentRoute(String vnId) throws SQLException {
        return routeRepository.findCurrent(vnId);
    }

    public Optional<LibraryEntry> find(String vnId) throws SQLException {
        return libraryRepository.findByVnId(vnId);
    }

    /** true si alguna VN de la lista tiene portada en VNDB pero no en disco. */
    public boolean hasMissingCovers(List<LibraryEntry> entries) {
        return entries.stream().map(LibraryEntry::vn).anyMatch(this::isCoverMissing);
    }

    /**
     * Descarga las portadas que falten (añadidas sin conexión, ficheros borrados a mano...).
     *
     * @return cuántas se han descargado
     */
    public int downloadMissingCovers() throws SQLException, InterruptedException {
        return downloadMissingCovers((done, total) -> { });
    }

    /**
     * Igual, avisando del progreso: {@code progress.accept(hechas, total)} al empezar (0 de total)
     * y tras cada portada (para "Descargando portadas · 3 de 8"). Se llama desde el hilo de fondo.
     */
    public int downloadMissingCovers(BiConsumer<Integer, Integer> progress)
            throws SQLException, InterruptedException {
        List<LibraryEntry> missing = findAll().stream().filter(e -> isCoverMissing(e.vn())).toList();
        int downloaded = 0;
        if (!missing.isEmpty()) {
            progress.accept(0, missing.size()); // que el aviso salga ya, no al acabar la primera
        }
        for (int i = 0; i < missing.size(); i++) {
            if (ensureCover(missing.get(i).vn())) {
                downloaded++;
            }
            progress.accept(i + 1, missing.size());
        }
        return downloaded;
    }

    /** true si esta VN tiene portada en VNDB que aún no está en disco (para dibujar un hueco). */
    public boolean coverPending(VisualNovel vn) {
        return isCoverMissing(vn);
    }

    private boolean isCoverMissing(VisualNovel vn) {
        return vn.imageUrl() != null && !imageCache.isCached(vn.imagePath());
    }

    /** Descarga la portada si falta. Devuelve true si ha descargado algo. */
    private boolean ensureCover(VisualNovel vn) throws SQLException, InterruptedException {
        if (!isCoverMissing(vn)) {
            return false;
        }
        try {
            String path = imageCache.download(vn.imageUrl(), "vn", vn.id());
            vnRepository.updateImagePath(vn.id(), path);
            return true;
        } catch (IOException e) {
            // No es grave: la VN se ve con un hueco "Sin portada" y se reintenta más adelante.
            LOG.log(System.Logger.Level.WARNING, "No se pudo descargar la portada de " + vn.id(), e);
            return false;
        }
    }
}

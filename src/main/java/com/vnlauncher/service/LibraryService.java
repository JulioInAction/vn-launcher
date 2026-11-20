package com.vnlauncher.service;

import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private final ImageCacheService imageCache;
    private final Clock clock; // inyectado para que los tests puedan fijar la hora

    public LibraryService(VnRepository vnRepository, LibraryRepository libraryRepository,
                          ImageCacheService imageCache, Clock clock) {
        this.vnRepository = vnRepository;
        this.libraryRepository = libraryRepository;
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

    public void remove(String vnId) throws SQLException {
        libraryRepository.remove(vnId);
    }

    public boolean isInLibrary(String vnId) throws SQLException {
        return libraryRepository.contains(vnId);
    }

    public List<LibraryEntry> findAll() throws SQLException {
        return libraryRepository.findAll();
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
        int downloaded = 0;
        for (LibraryEntry entry : findAll()) {
            if (isCoverMissing(entry.vn()) && ensureCover(entry.vn())) {
                downloaded++;
            }
        }
        return downloaded;
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

package com.vnlauncher.service;

import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.VndbException;
import com.vnlauncher.api.dto.LanguageDto;
import com.vnlauncher.api.dto.ReleaseDto;
import com.vnlauncher.api.dto.ReleaseImageDto;
import com.vnlauncher.db.repo.LibraryRepository;
import com.vnlauncher.model.CoverOption;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Cambiar portada": la galería con las imágenes de cada edición de la VN y la portada que el
 * usuario elige (library_entry.cover_override).
 *
 * <p>Coste: 1 petición a VNDB al abrir la galería (/release). Las miniaturas vienen del servidor
 * de imágenes, que no cuenta para el límite; la imagen grande solo se descarga al confirmar.
 */
public class CoverService {

    /** Las portadas elegidas se guardan como covers/ID-... (relativo a la carpeta de datos). */
    static final String COVERS_FOLDER = "covers";

    private final VndbClient vndb;
    private final LibraryRepository library;
    private final ImageCacheService images;
    private final AppPaths paths;
    private final Clock clock;

    public CoverService(VndbClient vndb, LibraryRepository library, ImageCacheService images,
                        AppPaths paths, Clock clock) {
        this.vndb = vndb;
        this.library = library;
        this.images = images;
        this.paths = paths;
        this.clock = clock;
    }

    // =========================================================== galería

    /** Imágenes de las ediciones de la VN que pueden servir de portada. Gasta 1 petición. */
    public List<CoverOption> vndbOptions(String vnId) throws VndbException {
        return toOptions(vndb.releasesWithImages(vnId), vnId);
    }

    /**
     * Ediciones → opciones de la galería:
     * <ul>
     *   <li>solo imágenes de ESTA VN (en una edición con varias VNs, cada imagen dice de cuál es;
     *       null = de todas);</li>
     *   <li>sin repetidas (la misma imagen puede estar en varias ediciones);</li>
     *   <li>primero las cajas frontales y las digitales; dentro de cada tipo, por fecha de la edición
     *       (las ediciones ya llegan ordenadas así, y el orden se conserva).</li>
     * </ul>
     */
    static List<CoverOption> toOptions(List<ReleaseDto> releases, String vnId) {
        // LinkedHashMap: no repite claves y recuerda el orden de inserción
        Map<String, CoverOption> byId = new LinkedHashMap<>();
        for (ReleaseDto release : releases) {
            if (release.images() == null) {
                continue;
            }
            for (ReleaseImageDto image : release.images()) {
                if (image.vn() != null && !image.vn().equals(vnId)) {
                    continue;
                }
                byId.putIfAbsent(image.id(), new CoverOption(image.id(), image.url(),
                        image.thumbnail() != null ? image.thumbnail() : image.url(),
                        release.title(), languageOf(image, release), image.type(),
                        image.sexual(), image.violence()));
            }
        }
        List<CoverOption> options = new ArrayList<>(byId.values());
        // sort es "estable": a igual tipo, se mantiene el orden por fecha de antes
        options.sort(Comparator.comparingInt(CoverOption::typeOrder));
        return options;
    }

    /** Idioma de la imagen; si la imagen no lo dice, el primero de la edición. */
    private static String languageOf(ReleaseImageDto image, ReleaseDto release) {
        if (image.languages() != null && !image.languages().isEmpty()) {
            return image.languages().getFirst();
        }
        List<LanguageDto> langs = release.languages();
        return langs == null || langs.isEmpty() ? null : langs.getFirst().lang();
    }

    /** Miniatura en disco (la descarga si aún no está). Se guardan en la caché: images/thumbs/. */
    public Path thumbnail(CoverOption option) throws IOException, InterruptedException {
        String relative = "images/thumbs/" + option.imageId() + ImageCacheService.extensionOf(option.thumbnailUrl());
        if (!images.isCached(relative)) {
            images.download(option.thumbnailUrl(), "thumbs", option.imageId());
        }
        return images.resolve(relative);
    }

    // =========================================================== elegir

    /** Usa como portada una imagen de VNDB: descarga la imagen grande y la apunta en la BD. */
    public void useVndbImage(String vnId, CoverOption option) throws IOException, InterruptedException, SQLException {
        String relative = COVERS_FOLDER + "/" + vnId + "-" + option.imageId() + ImageCacheService.extensionOf(option.url());
        images.downloadTo(option.url(), paths.root().resolve(relative));
        setOverride(vnId, relative);
    }

    /**
     * Usa como portada un archivo del usuario. Se COPIA a la carpeta de datos: si luego borra o
     * mueve el original, la portada sigue ahí. El nombre lleva la hora para no pisar el anterior
     * mientras alguna vista todavía lo esté mostrando.
     */
    public void useFile(String vnId, Path file) throws IOException, SQLException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : ".png";
        String relative = COVERS_FOLDER + "/" + vnId + "-propia-" + clock.millis() + extension;
        Path target = paths.root().resolve(relative);
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        setOverride(vnId, relative);
    }

    /** "Volver a la de VNDB": quita la portada elegida (y borra su archivo). */
    public void resetToVndb(String vnId) throws SQLException, IOException {
        setOverride(vnId, null);
    }

    /** Guarda la nueva portada y borra el archivo de la anterior, que ya no usa nadie. */
    private void setOverride(String vnId, String relative) throws SQLException, IOException {
        String previous = library.findByVnId(vnId).map(LibraryEntry::coverOverride).orElse(null);
        library.updateCoverOverride(vnId, relative);
        if (previous != null && !previous.equals(relative) && previous.startsWith(COVERS_FOLDER + "/")) {
            Files.deleteIfExists(paths.root().resolve(previous));
        }
    }

    // =========================================================== mostrar

    /**
     * La portada que hay que mostrar: la elegida si existe el archivo; si no, la de VNDB si ya está
     * descargada; si tampoco, null (hueco o iniciales).
     */
    public Path effectiveCover(LibraryEntry entry) {
        if (entry.coverOverride() != null) {
            Path chosen = paths.root().resolve(entry.coverOverride());
            if (Files.isRegularFile(chosen)) {
                return chosen;
            }
        }
        String vnCover = entry.vn().imagePath();
        return images.isCached(vnCover) ? images.resolve(vnCover) : null;
    }

    /** true si la portada que se ve es una elegida por el usuario (y no la de VNDB). */
    public boolean hasOverride(LibraryEntry entry) {
        return entry.coverOverride() != null && Files.isRegularFile(paths.root().resolve(entry.coverOverride()));
    }

    /** Si la portada elegida es la imagen de VNDB con este id (para marcarla como "Actual"). */
    public static boolean isCurrent(LibraryEntry entry, CoverOption option) {
        return entry.coverOverride() != null
                && entry.coverOverride().startsWith(COVERS_FOLDER + "/" + entry.vn().id() + "-" + option.imageId() + ".");
    }

    /** Si la portada elegida es un archivo propio del usuario. */
    public static boolean isCustom(LibraryEntry entry) {
        return entry.coverOverride() != null
                && entry.coverOverride().startsWith(COVERS_FOLDER + "/" + entry.vn().id() + "-propia-");
    }
}

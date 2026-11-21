package com.vnlauncher.service;

import com.vnlauncher.model.VnScreenshot;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Imágenes de las capturas de pantalla: miniaturas y tamaño completo, en la caché de imágenes
 * (images/shots/), igual que las portadas. «Vaciar caché» las borra y se vuelven a descargar
 * cuando hacen falta.
 *
 * <p>El nombre del archivo sale de la URL ("https://t.vndb.org/sf/03/12303.jpg" →
 * images/shots/sf-03-12303.jpg), así que no hace falta guardar la ruta en la base de datos: para
 * saber si una captura ya está descargada basta con mirar si existe su archivo.
 *
 * <p>Las imágenes vienen de t.vndb.org: no cuentan para el límite de peticiones de la API.
 */
public class ScreenshotService {

    static final String FOLDER = "shots";

    private final ImageCacheService images;
    /**
     * Un candado por archivo: la galería y el visor pueden pedir la misma imagen a la vez (p. ej. al
     * precargar la siguiente). Sin esto, dos descargas escribirían en el mismo archivo temporal.
     */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public ScreenshotService(ImageCacheService images) {
        this.images = images;
    }

    /** La miniatura en disco, si ya está descargada. */
    public Optional<Path> cachedThumbnail(VnScreenshot shot) {
        return cached(thumbnailUrl(shot));
    }

    /** La imagen grande en disco, si ya está descargada. */
    public Optional<Path> cachedImage(VnScreenshot shot) {
        return cached(shot.url());
    }

    /** La miniatura, descargándola si hace falta. Llamar en segundo plano. */
    public Path thumbnail(VnScreenshot shot) throws IOException, InterruptedException {
        return download(thumbnailUrl(shot));
    }

    /** La imagen grande, descargándola si hace falta. Llamar en segundo plano. */
    public Path image(VnScreenshot shot) throws IOException, InterruptedException {
        return download(shot.url());
    }

    /** Si VNDB no da miniatura, se usa la grande (es el mismo archivo: no se descarga dos veces). */
    private static String thumbnailUrl(VnScreenshot shot) {
        return shot.thumbnailUrl() != null ? shot.thumbnailUrl() : shot.url();
    }

    private Optional<Path> cached(String url) {
        String relative = relativePath(url);
        return images.isCached(relative) ? Optional.of(images.resolve(relative)) : Optional.empty();
    }

    private Path download(String url) throws IOException, InterruptedException {
        String relative = relativePath(url);
        synchronized (locks.computeIfAbsent(relative, key -> new Object())) {
            // Puede que otro hilo la acabe de descargar mientras esperábamos el candado
            if (!images.isCached(relative)) {
                images.download(url, FOLDER, cacheName(url));
            }
        }
        return images.resolve(relative);
    }

    private static String relativePath(String url) {
        return "images/" + FOLDER + "/" + cacheName(url) + ImageCacheService.extensionOf(url);
    }

    /**
     * Nombre del archivo (sin extensión) a partir de la ruta de la URL, con guiones en vez de
     * barras: "/sf/03/12303.jpg" → "sf-03-12303". Así la miniatura ("st") y la grande ("sf") de la
     * misma captura no se pisan.
     */
    static String cacheName(String url) {
        String path = URI.create(url).getPath();
        int dot = path.lastIndexOf('.');
        if (dot > path.lastIndexOf('/')) {
            path = path.substring(0, dot);
        }
        String name = path.replaceAll("^/+", "").replaceAll("[^A-Za-z0-9_-]+", "-");
        return name.isEmpty() ? Integer.toHexString(url.hashCode()) : name;
    }
}

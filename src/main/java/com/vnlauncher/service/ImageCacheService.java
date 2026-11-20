package com.vnlauncher.service;

import com.vnlauncher.util.AppPaths;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

/**
 * Descarga imágenes de VNDB a la carpeta local y sabe dónde están.
 *
 * <p>Las imágenes vienen de t.vndb.org, que es un servidor distinto de la API y NO cuenta para
 * el límite de 200 peticiones. Aun así, cada imagen se descarga una sola vez: quien llama comprueba
 * antes con {@link #isCached} si ya la tiene.
 */
public class ImageCacheService {

    private static final System.Logger LOG = System.getLogger(ImageCacheService.class.getName());

    private final HttpClient http;
    private final AppPaths paths;

    public ImageCacheService(HttpClient http, AppPaths paths) {
        this.http = http;
        this.paths = paths;
    }

    /**
     * Descarga {@code url} como images/{folder}/{id}.{ext} y devuelve esa ruta RELATIVA,
     * que es lo que se guarda en la base de datos.
     *
     * <p>Si ya hay un fichero con ese nombre, se REEMPLAZA. Antes se daba por bueno, pero con
     * "Actualizar desde VNDB" la portada puede haber cambiado de URL y seguir llamándose v4.jpg:
     * nos quedaríamos con la vieja. Para no repetir descargas, quien llama mira antes {@link #isCached}.
     *
     * @param folder "vn" para portadas, "char" para personajes
     */
    public String download(String url, String folder, String id) throws IOException, InterruptedException {
        // Barras "/" también en Windows: Path las acepta, y así la ruta guardada en la BD
        // es la misma en cualquier sistema.
        String relative = "images/" + folder + "/" + id + extensionOf(url);
        Path target = resolve(relative);
        Files.createDirectories(target.getParent());

        // Se descarga a un fichero temporal ".part" y al terminar se renombra. Si la descarga
        // se corta a medias (sin red, app cerrada), nunca queda una imagen rota con el nombre
        // definitivo que luego confundiríamos con una descarga completa.
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "vn-launcher/0.1")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(temp);
            throw new IOException("Error " + response.statusCode() + " descargando " + url);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOG.log(System.Logger.Level.INFO, "Imagen descargada: {0}", relative);
        return relative;
    }

    /** Ruta relativa de la BD → ruta absoluta en disco. */
    public Path resolve(String relativePath) {
        return paths.root().resolve(relativePath);
    }

    /** true si la ruta no es null y el fichero existe (puede haberse borrado a mano). */
    public boolean isCached(String relativePath) {
        return relativePath != null && Files.exists(resolve(relativePath));
    }

    /** Carpeta con todas las imágenes descargadas (la "caché"). */
    public Path cacheDir() {
        return paths.imagesDir();
    }

    /** Lo que ocupan en disco todas las imágenes descargadas, en bytes. */
    public long cacheSizeBytes() throws IOException {
        if (!Files.isDirectory(paths.imagesDir())) {
            return 0;
        }
        // Files.walk recorre la carpeta y todas sus subcarpetas; hay que cerrar el Stream (try)
        try (var files = Files.walk(paths.imagesDir())) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    return 0; // un archivo que desaparece mientras contamos: no pasa nada
                }
            }).sum();
        }
    }

    /**
     * Borra todas las imágenes descargadas (deja las carpetas). No hay que tocar la base de datos:
     * isCached() comprueba si el archivo existe, así que lo que falte se vuelve a descargar solo
     * (portadas al abrir la biblioteca, personajes al abrir su ficha).
     *
     * @return cuántos archivos se han borrado
     */
    public int clearCache() throws IOException {
        if (!Files.isDirectory(paths.imagesDir())) {
            return 0;
        }
        try (var files = Files.walk(paths.imagesDir())) {
            List<Path> toDelete = files.filter(Files::isRegularFile).toList();
            for (Path file : toDelete) {
                Files.deleteIfExists(file);
            }
            return toDelete.size();
        }
    }

    /** ".jpg" a partir de "https://t.vndb.org/cv/95/75895.jpg"; ".jpg" si no se puede saber. */
    static String extensionOf(String url) {
        String path = URI.create(url).getPath();
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot > slash && path.length() - dot <= 5) { // extensiones cortas: .jpg .png .webp
            return path.substring(dot).toLowerCase();
        }
        return ".jpg";
    }
}

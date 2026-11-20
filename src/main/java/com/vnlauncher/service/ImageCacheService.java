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

/**
 * Descarga imágenes de VNDB a la carpeta local y sabe dónde están.
 *
 * <p>Las imágenes vienen de t.vndb.org, que es un servidor distinto de la API y NO cuenta para
 * el límite de 200 peticiones. Aun así, cada imagen se descarga una sola vez: si el fichero
 * ya existe, no se vuelve a pedir.
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
     * @param folder "vn" para portadas, "char" para personajes (Fase 3)
     */
    public String download(String url, String folder, String id) throws IOException, InterruptedException {
        // Barras "/" también en Windows: Path las acepta, y así la ruta guardada en la BD
        // es la misma en cualquier sistema.
        String relative = "images/" + folder + "/" + id + extensionOf(url);
        Path target = resolve(relative);
        if (Files.exists(target)) {
            return relative;
        }
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

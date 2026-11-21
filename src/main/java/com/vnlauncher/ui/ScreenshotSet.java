package com.vnlauncher.ui;

import com.vnlauncher.model.VnScreenshot;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ScreenshotService;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Las capturas de una VN tal como las usan la galería de la ficha y el visor: cuáles son sensibles,
 * cuáles ha destapado ya el usuario (lo que destapa en el visor sigue destapado en la ficha), sus
 * miniaturas y sus imágenes grandes.
 *
 * <p>Las descargas van en segundo plano (ExecutorService) y los avisos llegan siempre en el hilo
 * de la interfaz. Todo lo demás se usa solo desde el hilo de la interfaz.
 */
final class ScreenshotSet {

    /** Ancho al que se cargan las miniaturas (en píxeles lógicos; se multiplica por la escala). */
    private static final double THUMB_WIDTH = 200;
    /** Imágenes grandes que se guardan en memoria: la actual, la anterior y la siguiente, y poco más. */
    private static final int LARGE_IN_MEMORY = 6;

    private final List<VnScreenshot> shots;
    private final ScreenshotService service;
    private final ExecutorService executor;
    private final boolean[] sensitive;
    private final Set<Integer> revealed = new HashSet<>();
    private final Image[] thumbnails;
    private final List<IntConsumer> thumbnailListeners = new ArrayList<>();
    /**
     * Imágenes grandes ya creadas, por "índice@ancho". LinkedHashMap con accessOrder = true ordena
     * de la menos a la más usada; removeEldestEntry tira la menos usada al pasar del tope (una
     * caché LRU en tres líneas).
     */
    private final Map<String, Image> large = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > LARGE_IN_MEMORY;
        }
    };

    ScreenshotSet(List<VnScreenshot> shots, ScreenshotService service, ExecutorService executor,
                  ContentFilter filter) {
        this.shots = List.copyOf(shots);
        this.service = service;
        this.executor = executor;
        this.sensitive = new boolean[shots.size()];
        this.thumbnails = new Image[shots.size()];
        for (int i = 0; i < shots.size(); i++) {
            VnScreenshot shot = shots.get(i);
            sensitive[i] = filter.isSensitive(shot.sexual(), shot.violence());
        }
    }

    int size() {
        return shots.size();
    }

    boolean isSensitive(int index) {
        return sensitive[index];
    }

    boolean isRevealed(int index) {
        return revealed.contains(index);
    }

    void reveal(int index) {
        revealed.add(index);
    }

    /** La miniatura, o null si aún no está cargada. */
    Image thumbnail(int index) {
        return thumbnails[index];
    }

    /** Avisa (en el hilo de la interfaz) cada vez que se carga la miniatura de una captura. */
    void addThumbnailListener(IntConsumer listener) {
        thumbnailListeners.add(listener);
    }

    void removeThumbnailListener(IntConsumer listener) {
        thumbnailListeners.remove(listener);
    }

    /**
     * Miniaturas: las que ya están en disco se cargan enseguida; las que faltan se descargan una
     * detrás de otra en segundo plano. Sin conexión se quedan vacías (sin error a la vista).
     */
    void loadThumbnails() {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < shots.size(); i++) {
            Optional<Path> cached = service.cachedThumbnail(shots.get(i));
            if (cached.isPresent()) {
                setThumbnail(i, cached.get());
            } else {
                missing.add(i);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        executor.submit(() -> {
            for (int i : missing) {
                try {
                    Path file = service.thumbnail(shots.get(i));
                    Platform.runLater(() -> setThumbnail(i, file));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    // Sin conexión, etc.: se intentará la próxima vez que se abra la ficha
                    return;
                }
            }
        });
    }

    private void setThumbnail(int index, Path file) {
        double width = THUMB_WIDTH * CoverView.outputScale();
        Image image = new Image(file.toUri().toString(), width, 0, true, true, true);
        thumbnails[index] = image;
        whenLoaded(image, loaded -> List.copyOf(thumbnailListeners).forEach(listener -> listener.accept(index)));
    }

    /**
     * La imagen grande, cargada a {@code width} píxeles lógicos de ancho (0 = tamaño original).
     * Si hace falta se descarga primero. {@code onLoaded} se llama en el hilo de la interfaz cuando
     * está lista del todo, o no se llama si no se pudo conseguir (sin conexión).
     */
    void largeImage(int index, double width, Consumer<Image> onLoaded) {
        String key = index + "@" + width;
        Image ready = large.get(key);
        if (ready != null) {
            whenLoaded(ready, onLoaded);
            return;
        }
        VnScreenshot shot = shots.get(index);
        Optional<Path> cached = service.cachedImage(shot);
        if (cached.isPresent()) {
            whenLoaded(createLarge(key, cached.get(), width), onLoaded);
            return;
        }
        executor.submit(() -> {
            try {
                Path file = service.image(shot);
                Platform.runLater(() -> {
                    Image image = large.containsKey(key) ? large.get(key) : createLarge(key, file, width);
                    whenLoaded(image, onLoaded);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Sin conexión: se queda la miniatura ampliada
            }
        });
    }

    private Image createLarge(String key, Path file, double width) {
        double requested = width <= 0 ? 0 : width * CoverView.outputScale();
        // Cargar en segundo plano (último true): una captura de 1920 × 1080 tarda en decodificarse
        Image image = new Image(file.toUri().toString(), requested, 0, true, true, true);
        large.put(key, image);
        return image;
    }

    /** Llama a {@code action} cuando la imagen esté cargada del todo (ya, si ya lo está). */
    private static void whenLoaded(Image image, Consumer<Image> action) {
        if (image.isError()) {
            return;
        }
        if (image.getProgress() >= 1) {
            action.accept(image);
            return;
        }
        image.progressProperty().addListener((obs, old, progress) -> {
            if (progress.doubleValue() >= 1 && !image.isError()) {
                action.accept(image);
            }
        });
    }
}

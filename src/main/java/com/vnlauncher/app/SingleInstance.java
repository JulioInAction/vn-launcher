package com.vnlauncher.app;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;

/**
 * Que solo haya una VN Launcher abierta a la vez. Dos ventanas sobre la misma base de datos se
 * pisarían los cambios, y restaurar una copia con otra ventana abierta la dejaría a medias.
 *
 * <p>Se hace con un bloqueo del sistema operativo sobre un archivo (vn-launcher.lock): solo un
 * proceso puede tenerlo. Si la app se cierra de golpe, el sistema lo suelta solo, así que nunca
 * queda "atascado" (cosa que sí pasaría con un simple archivo que se crea al abrir y se borra al
 * cerrar).
 */
public final class SingleInstance implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(SingleInstance.class.getName());
    private static final long RETRY_EVERY_MS = 200;

    private final FileChannel channel;
    private final FileLock lock;

    private SingleInstance(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Intenta quedarse con el bloqueo, reintentando durante {@code wait} (al reiniciar, la ventana
     * anterior puede tardar un momento en soltarlo).
     *
     * @return el bloqueo (hay que cerrarlo al salir), o vacío si otra VN Launcher lo tiene
     */
    public static Optional<SingleInstance> acquire(Path lockFile, Duration wait) throws IOException {
        Files.createDirectories(lockFile.getParent());
        long deadline = System.nanoTime() + wait.toNanos();
        while (true) {
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = null;
            try {
                lock = channel.tryLock(); // null: lo tiene otro proceso
            } catch (OverlappingFileLockException sameProcess) {
                // Lo tiene esta misma JVM (solo pasa en los tests, que piden dos veces el bloqueo)
            } catch (IOException e) {
                channel.close();
                throw e;
            }
            if (lock != null) {
                return Optional.of(new SingleInstance(channel, lock));
            }
            channel.close();
            if (System.nanoTime() >= deadline) {
                return Optional.empty();
            }
            try {
                Thread.sleep(RETRY_EVERY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    /** Suelta el bloqueo: desde ahora se puede abrir otra VN Launcher. */
    @Override
    public void close() {
        try {
            lock.release();
            channel.close();
        } catch (IOException e) {
            // Al terminar el proceso el sistema lo suelta igualmente
            LOG.log(System.Logger.Level.WARNING, "No se pudo soltar el bloqueo de instancia única", e);
        }
    }
}

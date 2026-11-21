package com.vnlauncher.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Properties;

/**
 * Algo que hay que terminar en el próximo arranque, antes de abrir la base de datos: sustituirla
 * por una copia de seguridad (y, en la fase siguiente, por un perfil importado).
 *
 * <p>Se apunta en operacion-pendiente.properties junto a la base nueva ya preparada (restaurar.db).
 * Si la app no consigue reiniciarse sola, se termina igualmente la próxima vez que se abra.
 *
 * @param kind     qué se está haciendo
 * @param dataDate de cuándo son los datos que se ponen (la fecha de la copia); para avisar luego
 */
public record PendingOperation(Kind kind, LocalDateTime dataDate) {

    public enum Kind {
        RESTORE("restaurar");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        static Kind fromKey(String key) throws IOException {
            return Arrays.stream(values())
                    .filter(k -> k.key.equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Operación pendiente desconocida: " + key));
        }
    }

    private static final String KIND = "operacion";
    private static final String DATE = "fecha";

    /** Lo escribe entero o nada (primero en un .part, luego se renombra). */
    public void write(Path file) throws IOException {
        Properties props = new Properties();
        props.setProperty(KIND, kind.key);
        props.setProperty(DATE, dataDate.toString());
        Path partial = file.resolveSibling(file.getFileName() + ".part");
        try (Writer out = Files.newBufferedWriter(partial, StandardCharsets.UTF_8)) {
            props.store(out, "VN Launcher: se aplica al arrancar. No lo edites.");
        }
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** @throws IOException si no se puede leer o no tiene lo que debe */
    public static PendingOperation read(Path file) throws IOException {
        Properties props = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(in);
        }
        try {
            return new PendingOperation(Kind.fromKey(props.getProperty(KIND)),
                    LocalDateTime.parse(props.getProperty(DATE, "")));
        } catch (DateTimeParseException e) {
            throw new IOException("Fecha no válida en " + file.getFileName(), e);
        }
    }
}

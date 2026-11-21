package com.vnlauncher.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vnlauncher.db.Database;
import com.vnlauncher.model.BackupKind;
import com.vnlauncher.model.ProfileManifest;
import com.vnlauncher.model.ProfilePreview;
import com.vnlauncher.util.AppPaths;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Exportar e importar el perfil: un .zip con la base de datos, las portadas propias y un
 * manifest.json con el resumen.
 * <pre>
 *   manifest.json
 *   library.db
 *   covers/v4-12345.jpg ...
 * </pre>
 * La caché de imágenes (images/) no va: se vuelve a descargar de VNDB.
 *
 * <p>Importar es como restaurar una copia: {@link #open} lo descomprime en pendiente/ y lo
 * comprueba, {@link #prepareImport} hace la copia «Antes de importar un perfil» y apunta la
 * operación, y al reiniciar {@link PendingOperations} pone la base y suma las portadas.
 *
 * <p>El zip puede venir de cualquier sitio, así que no se fía de él: solo extrae los nombres que
 * conoce (y nunca fuera de pendiente/), con un tope de tamaño, y comprueba la base entera.
 */
public class ProfileService {

    private static final System.Logger LOG = System.getLogger(ProfileService.class.getName());

    /** Versión del formato del zip. Solo sube si una versión vieja de la app no podría leerlo. */
    static final int FORMAT = 1;
    static final String MANIFEST = "manifest.json";
    static final String DATABASE = "library.db";
    static final String COVERS = "covers/";
    /** Tope de lo que se descomprime: un zip de pocos KB puede inflarse a muchos GB ("bomba zip"). */
    static final int MAX_GB = 2;
    static final long MAX_BYTES = MAX_GB * 1024L * 1024 * 1024;
    static final int MAX_ENTRIES = 50_000;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    /** Los nombres que pone CoverService (v4-12345.jpg, v4-propia-1795186093000.png): nada más. */
    private static final Pattern COVER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,120}");
    private static final String FILE_NAME = "vn-launcher-perfil-%s.zip";
    private static final String APP = "VN Launcher";

    static final String NOT_A_PROFILE = "Este archivo no es un perfil de VN Launcher.";
    static final String DAMAGED = "El perfil está dañado: su base de datos no se puede leer.";
    static final String TOO_NEW = "Este perfil es de una versión más nueva de VN Launcher. "
            + "Actualiza la aplicación para poder importarlo.";
    static final String UNSAFE = "El perfil tiene archivos con nombres no permitidos, así que no se importa.";
    static final String TOO_BIG = "El perfil ocupa demasiado (más de %d GB sin comprimir), así que no se importa.";

    private final Database database;
    private final AppPaths paths;
    private final BackupService backupService;
    private final Clock clock;
    private final String appVersion;
    private final long maxBytes;
    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // campos de versiones futuras
            .enable(SerializationFeature.INDENT_OUTPUT);                 // legible si se abre a mano

    public ProfileService(Database database, AppPaths paths, BackupService backupService, Clock clock,
                          String appVersion) {
        this(database, paths, backupService, clock, appVersion, MAX_BYTES);
    }

    /** @param maxBytes tope de lo que se descomprime (los tests lo bajan para no escribir 2 GB) */
    ProfileService(Database database, AppPaths paths, BackupService backupService, Clock clock,
                   String appVersion, long maxBytes) {
        this.database = database;
        this.paths = paths;
        this.backupService = backupService;
        this.clock = clock;
        this.appVersion = appVersion;
        this.maxBytes = maxBytes;
    }

    /** «vn-launcher-perfil-2026-11-21.zip», el nombre que se propone al exportar. */
    public String suggestedFileName() {
        return String.format(FILE_NAME, LocalDate.now(clock));
    }

    // ====================================================================== exportar

    /**
     * Escribe el perfil en {@code zip}. Primero en un .part que se renombra al final: si falla a
     * medias (disco lleno, USB quitado), no queda un zip roto con el nombre bueno.
     */
    public ProfileManifest export(Path zip) throws IOException, SQLException {
        Path copy = paths.root().resolve("exportar.db");
        Path partial = zip.resolveSibling(zip.getFileName() + ".part");
        try {
            // Copia consistente (VACUUM INTO, como las copias de seguridad) aunque la app la esté usando
            Files.deleteIfExists(copy);
            database.backupTo(copy);
            List<Path> covers = exportableCovers();
            ProfileManifest manifest = manifestFor(copy, covers.size());
            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(partial)))) {
                out.putNextEntry(new ZipEntry(MANIFEST));
                out.write(json.writeValueAsBytes(manifest));
                out.closeEntry();
                out.putNextEntry(new ZipEntry(DATABASE));
                Files.copy(copy, out);
                out.closeEntry();
                for (Path cover : covers) {
                    out.putNextEntry(new ZipEntry(COVERS + cover.getFileName()));
                    Files.copy(cover, out);
                    out.closeEntry();
                }
            }
            Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.log(System.Logger.Level.INFO, "Perfil exportado: {0} novelas, {1} portadas",
                    manifest.novels(), manifest.covers());
            return manifest;
        } finally {
            Files.deleteIfExists(copy);
            Files.deleteIfExists(partial);
        }
    }

    /** Las portadas de covers/ (solo archivos con nombre de portada: lo mismo que acepta importar). */
    private List<Path> exportableCovers() throws IOException {
        if (!Files.isDirectory(paths.coversDir())) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(paths.coversDir())) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> COVER_NAME.matcher(f.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
    }

    private ProfileManifest manifestFor(Path db, int covers) throws SQLException {
        Database copy = new Database(db);
        String exportedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
        return new ProfileManifest(FORMAT, APP, appVersion, copy.schemaVersion(), exportedAt,
                (int) count(copy, "SELECT COUNT(*) FROM library_entry"),
                (int) count(copy, "SELECT COUNT(*) FROM route"),
                count(copy, "SELECT COALESCE(SUM(duration_seconds), 0) FROM play_session"),
                covers,
                (int) count(copy, "SELECT COUNT(*) FROM shelf"));
    }

    private static long count(Database db, String sql) throws SQLException {
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // ====================================================================== importar

    /**
     * Abre un perfil para importarlo: lo descomprime en pendiente/ y lo comprueba entero. Todavía
     * no cambia nada: si el usuario no confirma, {@link #discardPreview} lo borra.
     *
     * @throws InvalidDataFileException si no es un perfil, está dañado, es de una versión más nueva,
     *                                  tiene nombres peligrosos o es demasiado grande
     */
    public ProfilePreview open(Path zip) throws InvalidDataFileException, IOException, SQLException {
        PendingOperations.deleteRecursively(paths.pendingDir());
        try {
            Files.createDirectories(paths.stagedCoversDir());
            ProfileManifest manifest = readManifest(extract(zip));
            LocalDateTime exportedAt = parseDate(manifest.exportedAt());
            checkDatabase(paths.stagedDatabaseFile());
            int current = (int) count(database, "SELECT COUNT(*) FROM library_entry");
            return new ProfilePreview(zip.getFileName().toString(), manifest, exportedAt, current);
        } catch (InvalidDataFileException | IOException | SQLException | RuntimeException e) {
            discardPreview();
            throw e;
        }
    }

    /** «Cancelar» en el diálogo: se borra lo descomprimido. */
    public void discardPreview() {
        PendingOperations.deleteRecursively(paths.pendingDir());
    }

    /**
     * «Importar perfil»: hace la copia «Antes de importar un perfil» y apunta la operación. El
     * cambio en sí se hace al reiniciar (ver {@link PendingOperations}).
     */
    public void prepareImport(ProfilePreview preview) throws IOException, SQLException {
        if (!Files.isRegularFile(paths.stagedDatabaseFile())) {
            throw new IOException("No hay ningún perfil abierto para importar");
        }
        try {
            backupService.backup(BackupKind.BEFORE_IMPORT);
            new PendingOperation(PendingOperation.Kind.IMPORT, preview.exportedAt())
                    .write(paths.pendingOperationFile());
        } catch (IOException | SQLException | RuntimeException e) {
            discardPreview();
            throw e;
        }
        LOG.log(System.Logger.Level.INFO, "Importación preparada: {0}", preview.fileName());
    }

    /**
     * Saca del zip lo que se conoce: la base a pendiente/library.db y las portadas a
     * pendiente/covers/. Lo demás que sea inofensivo (algo de una versión futura) se ignora.
     *
     * @return el contenido de manifest.json
     */
    private byte[] extract(Path zip) throws InvalidDataFileException, IOException {
        // ZipFile lee el índice del final del zip (el "directorio central"): es lo que usan todos los
        // programas, así que se ve lo mismo que vería el usuario al abrirlo
        try (ZipFile file = new ZipFile(zip.toFile())) {
            byte[] manifest = null;
            boolean hasDatabase = false;
            long total = 0;
            int count = 0;
            Set<String> seen = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) {
                    throw new InvalidDataFileException(String.format(TOO_BIG, MAX_GB));
                }
                String name = entry.getName();
                if (!isSafeEntryName(name)) {
                    throw new InvalidDataFileException(UNSAFE);
                }
                if (!seen.add(name)) {
                    throw new InvalidDataFileException(NOT_A_PROFILE); // dos veces el mismo nombre
                }
                if (entry.isDirectory()) {
                    continue;
                }
                Path target;
                if (name.equals(MANIFEST)) {
                    try (InputStream in = file.getInputStream(entry)) {
                        manifest = readLimited(in, MAX_MANIFEST_BYTES);
                    }
                    continue;
                } else if (name.equals(DATABASE)) {
                    target = paths.stagedDatabaseFile();
                    hasDatabase = true;
                } else if (name.startsWith(COVERS)) {
                    String cover = name.substring(COVERS.length());
                    if (!COVER_NAME.matcher(cover).matches()) {
                        throw new InvalidDataFileException(UNSAFE);
                    }
                    target = paths.stagedCoversDir().resolve(cover);
                } else {
                    continue;
                }
                // Por si acaso: lo que se escribe tiene que quedar dentro de pendiente/
                if (!target.normalize().startsWith(paths.pendingDir().normalize())) {
                    throw new InvalidDataFileException(UNSAFE);
                }
                try (InputStream in = file.getInputStream(entry)) {
                    total += copyLimited(in, target, maxBytes - total);
                }
            }
            if (manifest == null || !hasDatabase) {
                throw new InvalidDataFileException(NOT_A_PROFILE);
            }
            return manifest;
        } catch (ZipException | IllegalArgumentException notAZip) {
            // No es un zip, o está roto, o tiene nombres que ni se pueden leer
            throw new InvalidDataFileException(NOT_A_PROFILE);
        }
    }

    /**
     * Un nombre que no puede salirse de la carpeta al extraerlo: ni absoluto, ni con «..», ni con
     * cosas de Windows («C:», «\», «archivo:flujo»), ni caracteres de control.
     */
    static boolean isSafeEntryName(String name) {
        if (name.isEmpty() || name.length() > 255 || name.startsWith("/")
                || name.contains("\\") || name.contains(":")) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (c < 0x20) {
                return false;
            }
        }
        for (String part : name.split("/", -1)) {
            if (part.equals("..") || part.equals(".")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Copia contando los bytes de verdad (el tamaño que dice el zip puede mentir).
     *
     * @param limit lo que aún se puede descomprimir
     */
    private static long copyLimited(InputStream in, Path target, long limit) throws IOException, InvalidDataFileException {
        long copied = 0;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                copied += n;
                if (copied > limit) {
                    throw new InvalidDataFileException(String.format(TOO_BIG, MAX_GB));
                }
                out.write(buffer, 0, n);
            }
        }
        return copied;
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException, InvalidDataFileException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            if (out.size() + n > limit) {
                throw new InvalidDataFileException(NOT_A_PROFILE); // ningún manifest ocupa tanto
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private ProfileManifest readManifest(byte[] bytes) throws InvalidDataFileException {
        ProfileManifest manifest;
        try {
            manifest = json.readValue(bytes, ProfileManifest.class);
        } catch (IOException e) {
            throw new InvalidDataFileException(NOT_A_PROFILE);
        }
        if (manifest == null || manifest.format() <= 0) {
            throw new InvalidDataFileException(NOT_A_PROFILE);
        }
        if (manifest.format() > FORMAT || manifest.schemaVersion() > Database.latestVersion()) {
            throw new InvalidDataFileException(TOO_NEW);
        }
        return manifest;
    }

    private static LocalDateTime parseDate(String text) throws InvalidDataFileException {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidDataFileException(NOT_A_PROFILE);
        }
    }

    /** La base del perfil: entera, sin referencias rotas y de un esquema que esta app conoce. */
    private static void checkDatabase(Path db) throws InvalidDataFileException {
        Database.FileCheck check = Database.inspect(db);
        if (!check.intact()) {
            throw new InvalidDataFileException(DAMAGED);
        }
        if (check.schemaVersion() <= 0) {
            throw new InvalidDataFileException(NOT_A_PROFILE);
        }
        if (check.schemaVersion() > Database.latestVersion()) {
            throw new InvalidDataFileException(TOO_NEW);
        }
    }
}

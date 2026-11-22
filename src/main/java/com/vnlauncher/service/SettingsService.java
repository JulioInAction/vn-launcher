package com.vnlauncher.service;

import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.BlurLevel;
import com.vnlauncher.model.Theme;
import com.vnlauncher.model.WindowBounds;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Ajustes con tipo: en vez de que cada clase sepa que la ruta de Locale Emulator se guarda
 * con la clave "locale_emulator.path" como texto, piden un {@code Optional<Path>} aquí.
 * La pantalla de Ajustes lee y escribe todo a través de esta clase.
 */
public class SettingsService {

    static final String LOCALE_EMULATOR_PATH = "locale_emulator.path";
    static final String THEME = "theme";
    static final String SHOW_SPOILERS = "content.show_spoilers";
    static final String BLUR = "content.blur";
    static final String WINDOW = "window";   // "x;y;ancho;alto;maximizada"
    static final String WEEKLY_BACKUPS = "backup.weekly";
    static final String LIBRARY_SORT = "library.sort";
    static final String UPDATES_ON_START = "updates.on_start";
    static final String UPDATES_NOTIFIED = "updates.notified";


    private final SettingsRepository repository;
    private final ContentFilter contentFilter;

    /**
     * @param contentFilter el filtro que usa toda la app; al cambiar el nivel de difuminado se
     *                      actualiza también, para que el cambio se note sin reiniciar
     */
    public SettingsService(SettingsRepository repository, ContentFilter contentFilter) {
        this.repository = repository;
        this.contentFilter = contentFilter;
    }

    // ------------------------------------------------------------- Locale Emulator

    /** Ruta de LEProc.exe, si el usuario la ha configurado. */
    public Optional<Path> localeEmulatorPath() throws SQLException {
        return repository.get(LOCALE_EMULATOR_PATH).filter(s -> !s.isBlank()).map(Path::of);
    }

    public void setLocaleEmulatorPath(Path path) throws SQLException {
        if (path == null) {
            repository.remove(LOCALE_EMULATOR_PATH);
        } else {
            repository.put(LOCALE_EMULATOR_PATH, path.toAbsolutePath().toString());
        }
    }

    // ------------------------------------------------------------- Apariencia

    /** Tema elegido por el usuario (oscuro, claro o según Windows); oscuro si nunca ha elegido. */
    public Theme theme() throws SQLException {
        return Theme.fromSetting(repository.get(THEME).orElse(null));
    }

    public void setTheme(Theme theme) throws SQLException {
        repository.put(THEME, theme.settingValue());
    }

    // ------------------------------------------------------------- Ventana

    /**
     * Posición y tamaño con que se cerró la ventana la última vez; vacío la primera vez (o si el
     * valor guardado no se entiende, que se trata igual que si no hubiera).
     */
    public Optional<WindowBounds> windowBounds() throws SQLException {
        return repository.get(WINDOW).flatMap(SettingsService::parseWindow);
    }

    public void setWindowBounds(WindowBounds b) throws SQLException {
        // Un solo valor "x;y;ancho;alto;maximizada": se guarda y se lee de una vez
        repository.put(WINDOW, Math.round(b.x()) + ";" + Math.round(b.y()) + ";" + Math.round(b.width())
                + ";" + Math.round(b.height()) + ";" + b.maximized());
    }

    static Optional<WindowBounds> parseWindow(String value) {
        String[] parts = value.split(";");
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            return Optional.of(new WindowBounds(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Boolean.parseBoolean(parts[4])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------- Contenido

    /** Si los personajes spoiler se muestran por defecto en las fichas (por defecto, no). */
    public boolean showSpoilers() throws SQLException {
        return repository.get(SHOW_SPOILERS).map(Boolean::parseBoolean).orElse(false);
    }

    public void setShowSpoilers(boolean show) throws SQLException {
        repository.put(SHOW_SPOILERS, String.valueOf(show));
    }

    /**
     * Copia de seguridad semanal (por defecto, sí). Las de antes de actualizar, importar o restaurar
     * se hacen siempre: este ajuste no las apaga.
     */
    public boolean weeklyBackups() throws SQLException {
        return repository.get(WEEKLY_BACKUPS).map(Boolean::parseBoolean).orElse(true);
    }

    public void setWeeklyBackups(boolean enabled) throws SQLException {
        repository.put(WEEKLY_BACKUPS, String.valueOf(enabled));
    }

    /** Buscar versiones nuevas al arrancar (por defecto, sí). «Comprobar ahora» funciona igualmente. */
    public boolean checkUpdatesOnStart() throws SQLException {
        return repository.get(UPDATES_ON_START).map(Boolean::parseBoolean).orElse(true);
    }

    public void setCheckUpdatesOnStart(boolean enabled) throws SQLException {
        repository.put(UPDATES_ON_START, String.valueOf(enabled));
    }

    /** La última versión nueva de la que ya se avisó (el punto de la barra lateral); vacío si ninguna. */
    public Optional<String> lastNotifiedUpdate() throws SQLException {
        return repository.get(UPDATES_NOTIFIED);
    }

    public void setLastNotifiedUpdate(String version) throws SQLException {
        repository.put(UPDATES_NOTIFIED, version);
    }

    /**
     * El «Ordenar por» de la biblioteca. Se guarda con el nombre del valor ({@code TITLE}); si falta o
     * ya no se reconoce (un perfil de otra versión), «Último jugado».
     */
    public LibrarySort librarySort() throws SQLException {
        return repository.get(LIBRARY_SORT).map(LibrarySort::fromSetting).orElse(LibrarySort.LAST_PLAYED);
    }

    public void setLibrarySort(LibrarySort sort) throws SQLException {
        repository.put(LIBRARY_SORT, sort.name());
    }

    public BlurLevel blurLevel() throws SQLException {
        return BlurLevel.fromSetting(repository.get(BLUR).orElse(null));
    }

    public void setBlurLevel(BlurLevel level) throws SQLException {
        repository.put(BLUR, level.settingValue());
        contentFilter.setLevel(level);
    }

    /** Aplica al filtro el nivel guardado. Se llama una vez al arrancar. */
    public void loadContentFilter() throws SQLException {
        contentFilter.setLevel(blurLevel());
    }
}

package com.vnlauncher.service;

import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.BlurLevel;
import com.vnlauncher.model.Theme;

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

    // ------------------------------------------------------------- Contenido

    /** Si los personajes spoiler se muestran por defecto en las fichas (por defecto, no). */
    public boolean showSpoilers() throws SQLException {
        return repository.get(SHOW_SPOILERS).map(Boolean::parseBoolean).orElse(false);
    }

    public void setShowSpoilers(boolean show) throws SQLException {
        repository.put(SHOW_SPOILERS, String.valueOf(show));
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

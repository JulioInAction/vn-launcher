package com.vnlauncher.service;

import com.vnlauncher.db.repo.SettingsRepository;
import com.vnlauncher.model.Theme;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Ajustes con tipo: en vez de que cada clase sepa que la ruta de Locale Emulator se guarda
 * con la clave "locale_emulator.path" como texto, piden un {@code Optional<Path>} aquí.
 * En la Fase 7 la pantalla de Ajustes usará esta misma clase.
 */
public class SettingsService {

    static final String LOCALE_EMULATOR_PATH = "locale_emulator.path";
    static final String THEME = "theme";

    private final SettingsRepository repository;

    public SettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    /** Ruta de LEProc.exe, si el usuario la ha configurado. */
    public Optional<Path> localeEmulatorPath() throws SQLException {
        return repository.get(LOCALE_EMULATOR_PATH).filter(s -> !s.isBlank()).map(Path::of);
    }

    /** Tema elegido por el usuario; oscuro si nunca ha elegido. */
    public Theme theme() throws SQLException {
        return Theme.fromSetting(repository.get(THEME).orElse(null));
    }

    public void setTheme(Theme theme) throws SQLException {
        repository.put(THEME, theme.settingValue());
    }

    public void setLocaleEmulatorPath(Path path) throws SQLException {
        if (path == null) {
            repository.remove(LOCALE_EMULATOR_PATH);
        } else {
            repository.put(LOCALE_EMULATOR_PATH, path.toAbsolutePath().toString());
        }
    }
}

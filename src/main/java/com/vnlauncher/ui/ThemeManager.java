package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.Theme;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Parent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Gestiona el tema de la ventana.
 *
 * <p>El usuario elige entre Oscuro, Claro y "Según el sistema" ({@link #chosen()}); lo que se ve en
 * pantalla es siempre oscuro o claro ({@link #effective()}). Con "Según el sistema" se pregunta a
 * JavaFX (22 o superior) qué modo de color usa el sistema con
 * {@code Platform.getPreferences().getColorScheme()}, y además se escucha esa propiedad: si
 * cambias el modo de color del sistema con la app abierta, el tema cambia al momento. Funciona en
 * Windows y macOS; en Linux, JavaFX lo deduce de los colores del tema de GTK.
 */
final class ThemeManager {

    private final Parent sceneRoot;
    private final AppContext ctx;
    private Theme chosen;
    /** Se avisa con el tema EFECTIVO (oscuro o claro) cada vez que cambia. */
    private final List<Consumer<Theme>> listeners = new CopyOnWriteArrayList<>();

    ThemeManager(Parent sceneRoot, AppContext ctx, Theme chosen) {
        this.sceneRoot = sceneRoot;
        this.ctx = ctx;
        this.chosen = chosen;
        // Cambios del modo de color del sistema (solo importan si se eligió "Según el sistema")
        Platform.getPreferences().colorSchemeProperty().addListener((obs, oldScheme, newScheme) -> {
            if (this.chosen == Theme.SYSTEM) {
                apply();
            }
        });
        apply();
    }

    Theme chosen() {
        return chosen;
    }

    /** Oscuro o claro: lo que se está viendo. */
    Theme effective() {
        if (chosen != Theme.SYSTEM) {
            return chosen;
        }
        return Platform.getPreferences().getColorScheme() == ColorScheme.DARK ? Theme.DARK : Theme.LIGHT;
    }

    void addListener(Consumer<Theme> listener) {
        listeners.add(listener);
        listener.accept(effective());
    }

    /** El usuario elige tema: se aplica y se guarda en la tabla setting (clave "theme"). */
    void choose(Theme theme) {
        chosen = theme;
        apply();
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.settingsService().setTheme(theme);
                    return null;
                },
                ignored -> { },
                error -> Dialogs.error(sceneRoot, "No se pudo guardar el tema: " + error.getMessage()));
    }

    private void apply() {
        Theme effective = effective();
        Themes.apply(sceneRoot, effective);
        listeners.forEach(l -> l.accept(effective));
    }
}

package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.app.AppInfo;
import com.vnlauncher.model.BlurLevel;
import com.vnlauncher.model.Theme;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Pantalla de Ajustes (docs/diseño/capturas/ajustes.png). Cada cambio se guarda al momento en la
 * tabla setting (no hay botón "Guardar").
 */
public class SettingsController {

    /** Lo que se carga en segundo plano al abrir la pantalla. */
    private record Loaded(Optional<Path> lePath, boolean showSpoilers, BlurLevel blur, long cacheBytes) {
    }

    private final AppContext ctx;
    private final ThemeManager themeManager;
    private final Consumer<String> urlOpener;

    @FXML private TextField lePathField;
    @FXML private Label leStatusLabel;
    @FXML private HBox themeBox;
    @FXML private HBox spoilersBox;
    @FXML private HBox blurBox;
    @FXML private Label dataDirLabel;
    @FXML private Label cacheSizeLabel;
    @FXML private Button clearCacheButton;
    @FXML private Text appNameText;
    @FXML private Text versionText;

    private final Segmented<Theme> theme =
            new Segmented<>(Arrays.asList(Theme.values()), Theme::label);
    private final Switch spoilers = new Switch();
    private final Segmented<BlurLevel> blur =
            new Segmented<>(Arrays.asList(BlurLevel.values()), BlurLevel::label);

    SettingsController(AppContext ctx, ThemeManager themeManager, Consumer<String> urlOpener) {
        this.ctx = ctx;
        this.themeManager = themeManager;
        this.urlOpener = urlOpener;
    }

    @FXML
    private void initialize() {
        themeBox.getChildren().add(theme);
        spoilersBox.getChildren().add(spoilers);
        blurBox.getChildren().add(blur);
        dataDirLabel.setText(ctx.paths().root().toString());
        appNameText.setText(AppInfo.NAME);
        versionText.setText(" · versión " + AppInfo.VERSION);

        // --- Tema: el actual, y al elegir otro se aplica y se guarda (ThemeManager)
        theme.setValue(themeManager.chosen());
        theme.valueProperty().addListener((obs, oldValue, chosen) -> {
            if (chosen != themeManager.chosen()) { // evita repetir si el cambio vino de fuera
                themeManager.choose(chosen);
            }
        });
        // Si se cambia con el botón de la barra lateral, reflejarlo aquí
        themeManager.addListener(effective -> theme.setValue(themeManager.chosen()));

        // --- Ruta de LEProc.exe: se guarda al pulsar Enter o al salir del campo
        lePathField.setOnAction(event -> saveLocaleEmulatorPath());
        lePathField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                saveLocaleEmulatorPath();
            }
        });

        // --- Valores guardados (en segundo plano: lee la BD y mide la caché)
        BackgroundTasks.run(ctx.background(),
                () -> new Loaded(ctx.settingsService().localeEmulatorPath(), ctx.settingsService().showSpoilers(),
                        ctx.settingsService().blurLevel(), ctx.imageCache().cacheSizeBytes()),
                loaded -> {
                    lePathField.setText(loaded.lePath().map(Path::toString).orElse(""));
                    renderLocaleEmulatorStatus(loaded.lePath().orElse(null));
                    spoilers.setSelected(loaded.showSpoilers());
                    blur.setValue(loaded.blur());
                    cacheSizeLabel.setText(formatBytes(loaded.cacheBytes()));
                    // Los listeners se añaden DESPUÉS de poner los valores iniciales (que no cuenten)
                    spoilers.selectedProperty().addListener((obs, was, now) ->
                            save(() -> ctx.settingsService().setShowSpoilers(now)));
                    blur.valueProperty().addListener((obs, was, now) ->
                            save(() -> ctx.settingsService().setBlurLevel(now)));
                },
                error -> Dialogs.error(lePathField, error.getMessage()));
    }

    // =========================================================== Locale Emulator

    @FXML
    private void onBrowseLocaleEmulator() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elige LEProc.exe (Locale Emulator)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("LEProc.exe", "LEProc.exe"),
                new FileChooser.ExtensionFilter("Ejecutables (*.exe)", "*.exe"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        Path current = currentLePath();
        if (current != null && current.getParent() != null && Files.isDirectory(current.getParent())) {
            chooser.setInitialDirectory(current.getParent().toFile());
        }
        File chosen = chooser.showOpenDialog(lePathField.getScene().getWindow());
        if (chosen != null) {
            lePathField.setText(chosen.getAbsolutePath());
            saveLocaleEmulatorPath();
        }
    }

    private Path currentLePath() {
        String text = lePathField.getText();
        return text == null || text.isBlank() ? null : Path.of(text.strip());
    }

    private void saveLocaleEmulatorPath() {
        Path path = currentLePath();
        renderLocaleEmulatorStatus(path);
        save(() -> ctx.settingsService().setLocaleEmulatorPath(path));
    }

    /** "✓ Encontrado" (azul), "No se encuentra en esa ruta" (-vn-danger) o "Sin configurar". */
    private void renderLocaleEmulatorStatus(Path path) {
        leStatusLabel.getStyleClass().removeAll("status-ok", "status-error", "meta");
        if (path == null) {
            leStatusLabel.setText("Sin configurar");
            leStatusLabel.getStyleClass().add("meta");
        } else if (Files.isRegularFile(path)) {
            leStatusLabel.setText("✓  Encontrado");
            leStatusLabel.getStyleClass().add("status-ok");
        } else {
            leStatusLabel.setText("✕  No se encuentra en esa ruta");
            leStatusLabel.getStyleClass().add("status-error");
        }
    }

    // =========================================================== Datos

    @FXML
    private void onOpenDataFolder() {
        // Un enlace file:/... abre la carpeta en el Explorador de Windows
        urlOpener.accept(ctx.paths().root().toUri().toString());
    }

    @FXML
    private void onClearCache() {
        if (!Dialogs.confirm(clearCacheButton, "¿Vaciar la caché de imágenes?",
                "Se borrarán las portadas e imágenes de personajes descargadas. Se volverán a descargar "
                        + "solas cuando hagan falta (necesitarás conexión).", "Vaciar caché", true)) {
            return;
        }
        clearCacheButton.setDisable(true);
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.imageCache().clearCache();
                    return ctx.imageCache().cacheSizeBytes();
                },
                size -> {
                    cacheSizeLabel.setText(formatBytes(size));
                    clearCacheButton.setDisable(false);
                },
                error -> {
                    clearCacheButton.setDisable(false);
                    Dialogs.error(clearCacheButton, error.getMessage());
                });
    }

    /** 0 → "0 KB", 1536 → "2 KB", 148 897 792 → "142 MB". */
    static String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1) {
            return Math.round(mb) + " MB";
        }
        return Math.round(bytes / 1024.0) + " KB";
    }

    // =========================================================== Acerca de

    @FXML
    private void onOpenVndb() {
        urlOpener.accept("https://vndb.org");
    }

    @FXML
    private void onOpenLocaleEmulator() {
        urlOpener.accept("https://github.com/xupefei/Locale-Emulator");
    }

    // =========================================================== utilidades

    /** Guarda en segundo plano (escribir en la BD no debe frenar la interfaz). */
    private void save(SqlAction action) {
        BackgroundTasks.run(ctx.background(), () -> {
                    action.run();
                    return null;
                },
                ignored -> { },
                error -> Dialogs.error(lePathField, "No se pudo guardar el ajuste: " + error.getMessage()));
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws Exception;
    }
}

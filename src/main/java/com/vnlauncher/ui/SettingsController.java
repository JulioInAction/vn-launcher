package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.app.AppInfo;
import com.vnlauncher.app.SystemInfo;
import com.vnlauncher.model.BackupInfo;
import com.vnlauncher.model.BlurLevel;
import com.vnlauncher.model.Theme;
import com.vnlauncher.service.InvalidBackupException;
import com.vnlauncher.util.Formats;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Pantalla de Ajustes (docs/diseño/capturas/ajustes.png). Cada cambio se guarda al momento en la
 * tabla setting (no hay botón "Guardar").
 */
public class SettingsController {

    /** Lo que se carga en segundo plano al abrir la pantalla. */
    private record Loaded(Optional<Path> lePath, boolean showSpoilers, BlurLevel blur, long cacheBytes,
                          boolean weeklyBackups, List<BackupInfo> backups) {
    }

    private static final String NO_BACKUPS = "Todavía no hay copias. La primera semanal se hace al abrir la app.";
    /** «Antes de actualizar a 1.0.0 · 2,4 MB» */
    private static final String BACKUP_DETAILS = "%s · %s";
    private static final String RESTORE = "Restaurar";
    private static final String GAME_RUNNING_TITLE = "Hay un juego abierto";
    private static final String GAME_RUNNING = "Cierra el juego antes de restaurar una copia: VN Launcher "
            + "se reinicia para aplicarla y se perdería el tiempo de juego de esta partida.";
    private static final String CANNOT_RESTORE_TITLE = "No se puede restaurar esta copia";
    private static final String CONFIRM_RESTORE_TITLE = "¿Restaurar esta copia?";
    /** «… como estaba el 21/11/2026, 10:42 (Semanal). …» */
    private static final String CONFIRM_RESTORE = "Tu biblioteca volverá a estar como estaba el %s (%s). "
            + "Lo que hayas cambiado después no estará, pero antes se guarda una copia de cómo está ahora, "
            + "por si quieres volver atrás. VN Launcher se reiniciará para aplicarla.";
    private static final String CONFIRM_RESTORE_BUTTON = "Restaurar y reiniciar";
    private static final String RESTART_MANUALLY_TITLE = "Vuelve a abrir VN Launcher";
    private static final String RESTART_MANUALLY = "La copia está lista, pero VN Launcher no ha podido "
            + "reiniciarse solo. Se va a cerrar: ábrelo otra vez para terminar de restaurarla.";
    private static final String COPY_SYSTEM_INFO = "Copiar información del sistema";
    private static final String COPIED = "✓  Copiada";

    private final AppContext ctx;
    private final ThemeManager themeManager;
    private final Consumer<String> urlOpener;
    private final BooleanSupplier relauncher;
    /** Los botones «Restaurar»: se desactivan todos mientras se prepara una. */
    private final List<Button> restoreButtons = new ArrayList<>();

    @FXML private TextField lePathField;
    @FXML private Label leStatusLabel;
    @FXML private HBox themeBox;
    @FXML private HBox spoilersBox;
    @FXML private HBox blurBox;
    @FXML private Label dataDirLabel;
    @FXML private Label cacheSizeLabel;
    @FXML private Button clearCacheButton;
    @FXML private HBox weeklyBackupsBox;
    @FXML private VBox backupList;
    @FXML private Button openLogsButton;
    @FXML private Button copySystemInfoButton;
    @FXML private Text appNameText;
    @FXML private Text versionText;

    private final Segmented<Theme> theme =
            new Segmented<>(Arrays.asList(Theme.values()), Theme::label);
    private final Switch spoilers = new Switch();
    private final Switch weeklyBackups = new Switch();
    private final Segmented<BlurLevel> blur =
            new Segmented<>(Arrays.asList(BlurLevel.values()), BlurLevel::label);

    SettingsController(AppContext ctx, ThemeManager themeManager, Consumer<String> urlOpener,
                       BooleanSupplier relauncher) {
        this.ctx = ctx;
        this.themeManager = themeManager;
        this.urlOpener = urlOpener;
        this.relauncher = relauncher;
    }

    @FXML
    private void initialize() {
        themeBox.getChildren().add(theme);
        spoilersBox.getChildren().add(spoilers);
        blurBox.getChildren().add(blur);
        weeklyBackupsBox.getChildren().add(weeklyBackups);
        dataDirLabel.setText(ctx.paths().root().toString());
        dataDirLabel.setTooltip(new Tooltip(ctx.paths().root().toString()));
        // Si no se pudo preparar el registro (carpeta sin permisos...), no hay carpeta que abrir
        openLogsButton.setDisable(ErrorReporter.logFile() == null);
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
                        ctx.settingsService().blurLevel(), ctx.imageCache().cacheSizeBytes(),
                        ctx.settingsService().weeklyBackups(), ctx.backupService().list()),
                loaded -> {
                    lePathField.setText(loaded.lePath().map(Path::toString).orElse(""));
                    renderLocaleEmulatorStatus(loaded.lePath().orElse(null));
                    spoilers.setSelected(loaded.showSpoilers());
                    blur.setValue(loaded.blur());
                    cacheSizeLabel.setText(formatBytes(loaded.cacheBytes()));
                    weeklyBackups.setSelected(loaded.weeklyBackups());
                    renderBackups(loaded.backups());
                    // Los listeners se añaden DESPUÉS de poner los valores iniciales (que no cuenten)
                    spoilers.selectedProperty().addListener((obs, was, now) ->
                            save(() -> ctx.settingsService().setShowSpoilers(now)));
                    blur.valueProperty().addListener((obs, was, now) ->
                            save(() -> ctx.settingsService().setBlurLevel(now)));
                    weeklyBackups.selectedProperty().addListener((obs, was, now) ->
                            save(() -> ctx.settingsService().setWeeklyBackups(now)));
                },
                "No se han podido cargar los ajustes.");
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
                    ErrorReporter.report("No se ha podido vaciar la caché de imágenes.", error);
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

    // =========================================================== Copias de seguridad

    /** Una fila por copia: «21/11/2026, 10:42   Antes de actualizar a 1.0.0 · 2,4 MB   [Restaurar]». */
    private void renderBackups(List<BackupInfo> backups) {
        backupList.getChildren().clear();
        restoreButtons.clear();
        if (backups.isEmpty()) {
            Label none = new Label(NO_BACKUPS);
            none.getStyleClass().add("muted");
            backupList.getChildren().add(none);
            return;
        }
        for (BackupInfo backup : backups) {
            Label date = new Label(Formats.DATE_TIME.format(backup.createdAt()));
            date.getStyleClass().add("backup-date"); // ancho fijo en el CSS: las descripciones, en columna
            Label what = new Label(String.format(BACKUP_DETAILS, backup.description(),
                    Formats.fileSize(backup.sizeBytes())));
            what.getStyleClass().add("text2");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button restore = new Button(RESTORE);
            restore.setMinWidth(Region.USE_PREF_SIZE);
            restore.setOnAction(event -> onRestore(backup));
            restoreButtons.add(restore);
            HBox row = new HBox(24, date, what, spacer, restore);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("backup-row");
            if (!backupList.getChildren().isEmpty()) {
                row.getStyleClass().add("divided"); // línea de separación con la fila de arriba
            }
            backupList.getChildren().add(row);
        }
    }

    /**
     * «Restaurar»: comprueba la copia, pide confirmación, la deja preparada (con una copia de cómo
     * está todo ahora) y reinicia la app, que la pone en su sitio al arrancar (ver RestoreService).
     */
    private void onRestore(BackupInfo backup) {
        // El tiempo de la partida en curso se guardaría en la base que se va a sustituir
        if (ctx.launchService().isAnyRunning()) {
            Dialogs.info(backupList, GAME_RUNNING_TITLE, GAME_RUNNING);
            return;
        }
        setRestoring(true);
        // Primero se comprueba (en segundo plano: lee la copia entera), para no preguntar en balde
        BackgroundTasks.run(ctx.background(),
                () -> {
                    ctx.restoreService().check(backup.file());
                    return backup;
                },
                checked -> confirmRestore(checked),
                this::restoreFailed);
    }

    private void confirmRestore(BackupInfo backup) {
        String message = String.format(CONFIRM_RESTORE, Formats.DATE_TIME.format(backup.createdAt()),
                backup.description());
        if (!Dialogs.confirm(backupList, CONFIRM_RESTORE_TITLE, message, CONFIRM_RESTORE_BUTTON, false)) {
            setRestoring(false);
            return;
        }
        BackgroundTasks.run(ctx.background(),
                () -> {
                    ctx.restoreService().prepare(backup);
                    return backup;
                },
                prepared -> restart(),
                this::restoreFailed);
    }

    /** La copia ya está preparada: se abre una VN Launcher nueva y esta se cierra. */
    private void restart() {
        if (!relauncher.getAsBoolean()) {
            // Lo pendiente se termina igualmente la próxima vez que se abra
            Dialogs.info(backupList, RESTART_MANUALLY_TITLE, RESTART_MANUALLY);
        }
        Platform.exit(); // Main.stop() cierra todo y suelta el bloqueo; la nueva está esperando
    }

    private void restoreFailed(Throwable error) {
        setRestoring(false);
        if (error instanceof InvalidBackupException invalid) {
            Dialogs.error(backupList, CANNOT_RESTORE_TITLE, invalid.getMessage());
        } else {
            ErrorReporter.report(StartupNotices.RESTORE_FAILED, error);
        }
    }

    private void setRestoring(boolean restoring) {
        restoreButtons.forEach(button -> button.setDisable(restoring));
    }

    @FXML
    private void onOpenBackupsFolder() {
        // La carpeta se crea con la primera copia; si aún no existe, se crea vacía para poder abrirla
        try {
            Files.createDirectories(ctx.backupService().dir());
        } catch (IOException e) {
            ErrorReporter.report("No se ha podido abrir la carpeta de copias.", e);
            return;
        }
        urlOpener.accept(ctx.backupService().dir().toUri().toString());
    }

    // =========================================================== Registro de errores

    @FXML
    private void onOpenLogsFolder() {
        ErrorReporter.openLogsFolder();
    }

    /** Versión de la app, de Java y del sistema, esquema de la base de datos... al portapapeles. */
    @FXML
    private void onCopySystemInfo() {
        BackgroundTasks.run(ctx.background(),
                () -> SystemInfo.current(ctx.database().schemaVersion(), ctx.paths().root()),
                info -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(info);
                    Clipboard.getSystemClipboard().setContent(content);
                    copySystemInfoButton.setText(COPIED);
                    PauseTransition reset = new PauseTransition(Duration.seconds(2));
                    reset.setOnFinished(event -> copySystemInfoButton.setText(COPY_SYSTEM_INFO));
                    reset.play();
                },
                "No se ha podido leer la información del sistema.");
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
                "No se ha podido guardar el ajuste.");
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws Exception;
    }
}

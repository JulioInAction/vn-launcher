package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.service.LaunchException;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Todo lo que pasa al pulsar "Jugar" (en la ficha o en la franja "Continuar" de la biblioteca):
 * lanzar el juego y, si algo falta, pedírselo al usuario y volver a intentarlo.
 *
 * <p>Cada motivo de fallo tiene su "remedio": si falta el ejecutable, elegirlo; si falta
 * Locale Emulator, indicar dónde está. Después se reintenta automáticamente.
 */
final class LaunchFlow {

    private final AppContext ctx;
    /** Nodo de la ventana sobre la que se abren los diálogos. */
    private final Node owner;

    LaunchFlow(AppContext ctx, Node owner) {
        this.ctx = ctx;
        this.owner = owner;
    }

    /**
     * @param onLaunched se ejecuta (en el hilo de la interfaz) si el juego se lanzó
     * @param onFailed   se ejecuta si no se lanzó (error o el usuario canceló un selector)
     */
    void play(String vnId, String title, Runnable onLaunched, Runnable onFailed) {
        BackgroundTasks.run(ctx.background(), () -> ctx.launchService().launch(vnId),
                game -> onLaunched.run(),
                error -> {
                    onFailed.run();
                    handle(error, vnId, title, onLaunched, onFailed);
                });
    }

    private void handle(Throwable error, String vnId, String title, Runnable onLaunched, Runnable onFailed) {
        if (!(error instanceof LaunchException launchError)) {
            Dialogs.error(owner, error.getMessage());
            return;
        }
        Runnable retry = () -> play(vnId, title, onLaunched, onFailed);
        // "switch" con flechas (Java 14+): no hace falta break y el compilador avisa si falta un caso
        switch (launchError.reason()) {
            case NO_EXECUTABLE -> chooseExecutable(vnId, title, null, retry);
            case EXECUTABLE_NOT_FOUND -> {
                Dialogs.error(owner, launchError.getMessage());
                chooseExecutable(vnId, title, null, retry);
            }
            case LOCALE_EMULATOR_NOT_CONFIGURED, LOCALE_EMULATOR_NOT_FOUND -> {
                info("Locale Emulator", launchError.getMessage()
                        + "\n\nA continuación elige el archivo LEProc.exe de la carpeta donde "
                        + "descomprimiste Locale Emulator. Solo se pide una vez.");
                chooseLocaleEmulator(retry);
            }
            case ALREADY_RUNNING -> info(title, "El juego ya está abierto. El tiempo se sigue contando.");
            case START_FAILED -> Dialogs.error(owner, launchError.getMessage());
        }
    }

    /**
     * Abre el selector de archivos; si el usuario elige uno, lo guarda y luego ejecuta {@code after}.
     *
     * @param currentExe ejecutable actual (para abrir el selector en su carpeta), o null
     */
    void chooseExecutable(String vnId, String title, String currentExe, Runnable after) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elige el ejecutable de " + title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ejecutables (*.exe)", "*.exe"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        if (currentExe != null) {
            File previousDir = Path.of(currentExe).getParent().toFile();
            if (previousDir.isDirectory()) {
                chooser.setInitialDirectory(previousDir);
            }
        }
        // showOpenDialog bloquea hasta que el usuario elige o cancela; null = canceló
        File chosen = chooser.showOpenDialog(owner.getScene().getWindow());
        if (chosen == null) {
            return;
        }
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setExecutable(vnId, chosen.toPath());
                    return null;
                },
                ignored -> after.run(),
                error -> Dialogs.error(owner, error.getMessage()));
    }

    void chooseLocaleEmulator(Runnable after) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elige LEProc.exe (Locale Emulator)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("LEProc.exe", "LEProc.exe"),
                new FileChooser.ExtensionFilter("Ejecutables (*.exe)", "*.exe"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File chosen = chooser.showOpenDialog(owner.getScene().getWindow());
        if (chosen == null || !Files.isRegularFile(chosen.toPath())) {
            return;
        }
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.settingsService().setLocaleEmulatorPath(chosen.toPath());
                    return null;
                },
                ignored -> after.run(),
                error -> Dialogs.error(owner, error.getMessage()));
    }

    void info(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(header);
        Dialogs.prepare(alert, owner);
        alert.showAndWait();
    }
}

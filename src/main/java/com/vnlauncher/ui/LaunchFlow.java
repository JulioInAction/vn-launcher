package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.service.LaunchException;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/**
 * Todo lo que pasa al pulsar "Jugar" (en la ficha o en la franja "Continuar" de la biblioteca):
 * lanzar el juego y, si algo falla, explicarlo con un diálogo que ofrece el remedio
 * (docs/diseño/capturas/error-ejecutable.png y error-locale-emulator.png).
 */
final class LaunchFlow {

    private final AppContext ctx;
    private final Navigator navigator;
    /** Nodo de la ventana sobre la que se abren los diálogos. */
    private Node owner;

    LaunchFlow(AppContext ctx, Navigator navigator, Node owner) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.owner = owner;
    }

    /**
     * @param onLaunched se ejecuta (en el hilo de la interfaz) si el juego se lanzó
     * @param onFailed   se ejecuta si no se lanzó (error o el usuario canceló)
     */
    void play(String vnId, String title, Runnable onLaunched, Runnable onFailed) {
        // Los diálogos se anclan a la RAÍZ de la ventana y no al botón pulsado: onFailed puede
        // volver a dibujar la pantalla (la franja "Continuar" crea un botón nuevo), y el botón viejo
        // ya no estaría en ninguna ventana; el diálogo saldría sin tema, sin fondo y sin ser modal.
        if (owner.getScene() != null) {
            owner = owner.getScene().getRoot();
        }
        play(vnId, title, false, onLaunched, onFailed);
    }

    private void play(String vnId, String title, boolean withoutLe, Runnable onLaunched, Runnable onFailed) {
        BackgroundTasks.run(ctx.background(), () -> ctx.launchService().launch(vnId, withoutLe),
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
        // "switch" con flechas (Java 14+): no hace falta break y el compilador avisa si falta un caso
        switch (launchError.reason()) {
            // Nunca se eligió el .exe: no es un error, se pide directamente
            case NO_EXECUTABLE -> chooseExecutable(vnId, title, null,
                    () -> play(vnId, title, onLaunched, onFailed));
            case EXECUTABLE_NOT_FOUND -> {
                if (askExecutableMoved(title, launchError.path())) {
                    chooseExecutable(vnId, title, null, () -> play(vnId, title, onLaunched, onFailed));
                }
            }
            case LOCALE_EMULATOR_NOT_CONFIGURED, LOCALE_EMULATOR_NOT_FOUND -> {
                String choice = askLocaleEmulatorMissing(launchError.reason());
                if ("without-le".equals(choice)) {
                    play(vnId, title, true, onLaunched, onFailed); // solo esta vez
                } else if ("settings".equals(choice)) {
                    navigator.showSettings();
                }
            }
            case ALREADY_RUNNING -> Dialogs.info(owner, title,
                    "El juego ya está abierto. El tiempo se sigue contando.");
            case START_FAILED -> Dialogs.error(owner, "No se pudo abrir el juego", launchError.getMessage());
        }
    }

    /** "No se encuentra el juego": true si el usuario quiere buscar el .exe. */
    private boolean askExecutableMoved(String title, Path missing) {
        // TextFlow: un párrafo con trozos de distinto estilo (el título en negrita)
        Text bold = new Text(title);
        bold.getStyleClass().add("text-strong");
        Text rest = new Text(" apuntaba a este archivo, pero ya no existe:");
        rest.getStyleClass().add("text-flow-normal");
        TextFlow intro = new TextFlow(bold, rest);

        Label path = new Label(missing == null ? "" : missing.toString());
        path.getStyleClass().add("mono-box");
        path.setMaxWidth(Double.MAX_VALUE);
        path.setWrapText(true);

        ModalDialog dialog = new ModalDialog(owner, 520)
                .title("No se encuentra el juego", true)
                .content(intro)
                .content(path)
                .text("Puede que hayas movido o desinstalado el juego. Tus rutas y tu tiempo de juego no se pierden.");
        dialog.button("cancel", "Cancelar", ModalDialog.ButtonKind.SECONDARY);
        dialog.button("browse", "Buscar el .exe", ModalDialog.ButtonKind.PRIMARY);
        return "browse".equals(dialog.showAndWait());
    }

    /** "No se encuentra Locale Emulator": devuelve "without-le", "settings" o null. */
    private String askLocaleEmulatorMissing(LaunchException.Reason reason) {
        String first = reason == LaunchException.Reason.LOCALE_EMULATOR_NOT_CONFIGURED
                ? "Esta novela está configurada para abrirse con Locale Emulator, pero aún no has "
                  + "indicado dónde está LEProc.exe."
                : "Esta novela está configurada para abrirse con Locale Emulator, pero no se encuentra "
                  + "LEProc.exe en la ruta de los ajustes.";
        ModalDialog dialog = new ModalDialog(owner, 520)
                .title("No se encuentra Locale Emulator", true)
                .text(first)
                .text("Sin él, puede que el juego muestre caracteres extraños o no arranque.");
        dialog.button("without-le", "Jugar sin Locale Emulator", ModalDialog.ButtonKind.SECONDARY);
        dialog.button("settings", "Ir a Ajustes", ModalDialog.ButtonKind.PRIMARY);
        return dialog.showAndWait();
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
}

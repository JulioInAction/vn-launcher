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
 * Todo lo que pasa al pulsar "Jugar" (en la ficha o en la franja "Continuar" de la biblioteca)
 * o "Configuración": lanzar el programa y, si algo falla, explicarlo con un diálogo que ofrece el
 * remedio (docs/design/mockups/error-missing-exe-dark.png y error-locale-emulator-dark.png).
 */
final class LaunchFlow {

    /** Qué se abre: el juego o su programa de configuración. Solo cambian los textos y el servicio. */
    private enum Target {
        GAME("el juego", "No se encuentra el juego", "Jugar sin Locale Emulator"),
        CONFIG("la configuración", "No se encuentra el programa de configuración", "Abrir sin Locale Emulator");

        final String what;
        final String notFoundTitle;
        final String withoutLeButton;

        Target(String what, String notFoundTitle, String withoutLeButton) {
            this.what = what;
            this.notFoundTitle = notFoundTitle;
            this.withoutLeButton = withoutLeButton;
        }
    }

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
        anchorToWindow();
        run(Target.GAME, vnId, title, false, onLaunched, onFailed);
    }

    /**
     * Abre el programa de configuración (config.exe...). Si aún no hay ninguno, lo pide.
     *
     * @param onOpened se ejecuta si se abrió
     * @param onFailed se ejecuta si no (puede que por el camino se haya elegido otro .exe)
     */
    void openConfig(String vnId, String title, Runnable onOpened, Runnable onFailed) {
        anchorToWindow();
        run(Target.CONFIG, vnId, title, false, onOpened, onFailed);
    }

    /**
     * Los diálogos se anclan a la RAÍZ de la ventana y no al botón pulsado: onFailed puede volver
     * a dibujar la pantalla (la franja "Continuar" crea un botón nuevo), y el botón viejo ya no
     * estaría en ninguna ventana; el diálogo saldría sin tema, sin fondo y sin ser modal.
     */
    private void anchorToWindow() {
        if (owner.getScene() != null) {
            owner = owner.getScene().getRoot();
        }
    }

    private void run(Target target, String vnId, String title, boolean withoutLe,
                     Runnable onLaunched, Runnable onFailed) {
        BackgroundTasks.run(ctx.background(), () -> {
                    if (target == Target.GAME) {
                        ctx.launchService().launch(vnId, withoutLe);
                    } else {
                        ctx.launchService().launchConfig(vnId, withoutLe);
                    }
                    return null;
                },
                ignored -> onLaunched.run(),
                error -> {
                    onFailed.run();
                    handle(error, target, vnId, title, onLaunched, onFailed);
                });
    }

    private void handle(Throwable error, Target target, String vnId, String title,
                        Runnable onLaunched, Runnable onFailed) {
        if (!(error instanceof LaunchException launchError)) {
            // No es un problema conocido al lanzar (falta el .exe, LE...): no debería pasar
            ErrorReporter.report(target == Target.GAME ? "No se ha podido abrir el juego."
                    : "No se ha podido abrir el programa de configuración.", error);
            return;
        }
        Runnable retry = () -> run(target, vnId, title, false, onLaunched, onFailed);
        // "switch" con flechas (Java 14+): no hace falta break y el compilador avisa si falta un caso
        switch (launchError.reason()) {
            // Nunca se eligió el .exe: no es un error, se pide directamente
            case NO_EXECUTABLE -> choose(target, vnId, title, null, retry);
            case EXECUTABLE_NOT_FOUND -> {
                if (askExecutableMoved(target, title, launchError.path())) {
                    choose(target, vnId, title, null, retry);
                }
            }
            case LOCALE_EMULATOR_NOT_CONFIGURED, LOCALE_EMULATOR_NOT_FOUND -> {
                String choice = askLocaleEmulatorMissing(target, launchError.reason());
                if ("without-le".equals(choice)) {
                    run(target, vnId, title, true, onLaunched, onFailed); // solo esta vez
                } else if ("settings".equals(choice)) {
                    navigator.showSettings();
                }
            }
            case ALREADY_RUNNING -> Dialogs.info(owner, title,
                    "El juego ya está abierto. El tiempo se sigue contando.");
            case START_FAILED -> Dialogs.error(owner, "No se pudo abrir " + target.what, launchError.getMessage());
        }
    }

    private void choose(Target target, String vnId, String title, String current, Runnable after) {
        if (target == Target.GAME) {
            chooseExecutable(vnId, title, current, after);
        } else {
            chooseConfig(vnId, title, current, after);
        }
    }

    /** "No se encuentra el juego" (o su configuración): true si el usuario quiere buscar el .exe. */
    private boolean askExecutableMoved(Target target, String title, Path missing) {
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
                .title(target.notFoundTitle, true)
                .content(intro)
                .content(path)
                .text(target == Target.GAME
                        ? "Puede que hayas movido o desinstalado el juego. Tus rutas y tu tiempo de juego no se pierden."
                        : "Puede que hayas movido el juego. Busca el programa de configuración en su nueva carpeta.");
        dialog.button("cancel", "Cancelar", ModalDialog.ButtonKind.SECONDARY);
        dialog.button("browse", "Buscar el .exe", ModalDialog.ButtonKind.PRIMARY);
        return "browse".equals(dialog.showAndWait());
    }

    /** "No se encuentra Locale Emulator": devuelve "without-le", "settings" o null. */
    private String askLocaleEmulatorMissing(Target target, LaunchException.Reason reason) {
        String first = reason == LaunchException.Reason.LOCALE_EMULATOR_NOT_CONFIGURED
                ? "Esta novela está configurada para abrirse con Locale Emulator, pero aún no has "
                  + "indicado dónde está LEProc.exe."
                : "Esta novela está configurada para abrirse con Locale Emulator, pero no se encuentra "
                  + "LEProc.exe en la ruta de los ajustes.";
        ModalDialog dialog = new ModalDialog(owner, 520)
                .title("No se encuentra Locale Emulator", true)
                .text(first)
                .text("Sin él, puede que " + target.what + " muestre caracteres extraños o no arranque.");
        dialog.button("without-le", target.withoutLeButton, ModalDialog.ButtonKind.SECONDARY);
        dialog.button("settings", "Ir a Ajustes", ModalDialog.ButtonKind.PRIMARY);
        return dialog.showAndWait();
    }

    /**
     * Abre el selector de archivos; si el usuario elige uno, lo guarda y luego ejecuta {@code after}.
     *
     * @param currentExe ejecutable actual (para abrir el selector en su carpeta), o null
     */
    void chooseExecutable(String vnId, String title, String currentExe, Runnable after) {
        File chosen = showExeChooser("Elige el ejecutable de " + title, currentExe);
        if (chosen == null) {
            return;
        }
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setExecutable(vnId, chosen.toPath());
                    return null;
                },
                ignored -> after.run(),
                "No se ha podido guardar el ejecutable.");
    }

    /**
     * Igual, para el programa de configuración. El selector se abre en la carpeta del programa
     * actual o, si no hay, en la del juego, que es donde suele estar (config.exe, 設定.exe...).
     *
     * @param startFrom ruta de un archivo de la carpeta donde empezar, o null
     */
    void chooseConfig(String vnId, String title, String startFrom, Runnable after) {
        File chosen = showExeChooser("Elige el programa de configuración de " + title, startFrom);
        if (chosen == null) {
            return;
        }
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setConfigExecutable(vnId, chosen.toPath());
                    return null;
                },
                ignored -> after.run(),
                "No se ha podido guardar el programa de configuración.");
    }

    /** Selector de .exe. Devuelve null si el usuario cancela. */
    private File showExeChooser(String windowTitle, String startFrom) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(windowTitle);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ejecutables (*.exe)", "*.exe"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        if (startFrom != null) {
            Path parent = Path.of(startFrom).getParent();
            if (parent != null && parent.toFile().isDirectory()) {
                chooser.setInitialDirectory(parent.toFile());
            }
        }
        // showOpenDialog bloquea hasta que el usuario elige o cancela; null = canceló
        return chooser.showOpenDialog(owner.getScene().getWindow());
    }
}

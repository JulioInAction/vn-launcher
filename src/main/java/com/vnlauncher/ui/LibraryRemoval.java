package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.LibraryEntry;
import javafx.scene.Node;

/**
 * «Quitar de la biblioteca», igual desde el «⋯» de la ficha que desde el menú de clic derecho de una
 * portada: no se deja con el juego abierto, se pide confirmación (diciendo que la portada elegida
 * también se borra, si la hay) y se quita en segundo plano (ver LibraryService.remove).
 */
final class LibraryRemoval {

    static final String GAME_RUNNING = "Cierra el juego antes de quitarlo de la biblioteca.";
    /** «¿Quitar "CLANNAD" de la biblioteca?» */
    static final String TITLE = "¿Quitar \"%s\" de la biblioteca?";
    static final String TEXT = "Se borrarán también sus rutas y el tiempo de juego registrado.";
    static final String TEXT_WITH_COVER = "Se borrarán también sus rutas, el tiempo de juego registrado y "
            + "la portada que elegiste.";
    static final String CONFIRM = "Quitar";
    static final String FAILED = "No se ha podido quitar la novela de la biblioteca.";

    private LibraryRemoval() {
    }

    /**
     * Pregunta y, si se confirma, quita la VN.
     *
     * @param owner     nodo de la ventana, para los diálogos
     * @param onStart   justo antes de quitarla (p. ej. desactivar el botón); no se llama si se cancela
     * @param onRemoved cuando ya está quitada
     * @param onFailed  si falla (el error ya se ha enseñado)
     */
    static void confirmAndRemove(AppContext ctx, Node owner, LibraryEntry entry,
                                 Runnable onStart, Runnable onRemoved, Runnable onFailed) {
        String vnId = entry.vn().id();
        if (ctx.launchService().isRunning(vnId)) {
            Dialogs.error(owner, GAME_RUNNING);
            return;
        }
        String consequences = entry.coverOverride() != null ? TEXT_WITH_COVER : TEXT;
        if (!Dialogs.confirm(owner, String.format(TITLE, entry.vn().title()), consequences, CONFIRM, true)) {
            return;
        }
        onStart.run();
        BackgroundTasks.runAndHandle(ctx.background(), () -> {
                    ctx.libraryService().remove(vnId);
                    return null; // Callable debe devolver algo; aquí no hay nada útil que devolver
                },
                ignored -> onRemoved.run(),
                error -> {
                    onFailed.run();
                    ErrorReporter.report(FAILED, error);
                });
    }
}

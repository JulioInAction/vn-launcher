package com.vnlauncher.ui;

import javafx.scene.Node;

/**
 * Diálogos de uso común, todos con el aspecto del diseño (ver {@link ModalDialog}).
 */
final class Dialogs {

    private Dialogs() {
    }

    /**
     * Pregunta antes de una acción. Devuelve true si el usuario confirma.
     *
     * @param danger true si la acción destruye algo (el botón de confirmar sale en -vn-danger)
     */
    static boolean confirm(Node owner, String title, String message, String confirmText, boolean danger) {
        ModalDialog dialog = new ModalDialog(owner, 460).title(title, danger).text(message);
        dialog.button("cancel", "Cancelar", ModalDialog.ButtonKind.SECONDARY);
        var ok = dialog.button("ok", confirmText, ModalDialog.ButtonKind.PRIMARY);
        if (danger) {
            ok.getStyleClass().setAll("button", "danger-solid");
        }
        return "ok".equals(dialog.showAndWait());
    }

    static void error(Node owner, String message) {
        error(owner, "Algo ha fallado", message);
    }

    static void error(Node owner, String title, String message) {
        ModalDialog dialog = new ModalDialog(owner, 460).title(title, true).text(message);
        dialog.button("ok", "Aceptar", ModalDialog.ButtonKind.PRIMARY);
        dialog.showAndWait();
    }

    static void info(Node owner, String title, String message) {
        ModalDialog dialog = new ModalDialog(owner, 460).title(title, false).text(message);
        dialog.button("ok", "Aceptar", ModalDialog.ButtonKind.PRIMARY);
        dialog.showAndWait();
    }
}

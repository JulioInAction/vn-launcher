package com.vnlauncher.ui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

/**
 * Utilidades para diálogos. Un diálogo de JavaFX abre su PROPIA ventana (con su propia escena),
 * así que no hereda la hoja de estilos de la ventana principal: hay que añadírsela.
 */
final class Dialogs {

    private Dialogs() {
    }

    /**
     * Aplica nuestro CSS y el tema actual, y asocia el diálogo a la ventana de {@code owner}
     * (así sale centrado encima de ella y no como una ventana suelta).
     */
    static void prepare(Dialog<?> dialog, Node owner) {
        dialog.getDialogPane().getStylesheets()
                .add(Views.class.getResource("styles.css").toExternalForm());
        // El DialogPane es la raíz de la escena del diálogo: le copiamos el tema de la ventana
        Themes.apply(dialog.getDialogPane(), Themes.current(owner));
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
    }

    /** Pregunta "¿Seguro?" y devuelve true si el usuario pulsa Aceptar. */
    static boolean confirm(Node owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
        prepare(alert, owner);
        // showAndWait bloquea hasta que el usuario responde (es un diálogo modal).
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    static void error(Node owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Algo ha fallado");
        prepare(alert, owner);
        alert.showAndWait();
    }
}

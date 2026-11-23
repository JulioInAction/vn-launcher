package com.vnlauncher.ui;

import com.vnlauncher.api.VndbException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * Aviso dentro de una pantalla (docs/design/mockups/error-no-connection-dark.png y error-rate-limit-dark.png).
 * design.md: naranja si es un problema (WARNING), azul si solo hay que esperar (WAIT).
 */
class Notice extends HBox {

    enum Kind { WARNING, WAIT }

    private static final String WARNING_ICON = "M12 3.5L21.5 20H2.5Z M12 9.5V14 M12 16.8V17";
    private static final String CLOCK_ICON = "M12 3A9 9 0 1 0 12.01 3Z M12 7V12L15.5 14";

    private final Label title = new Label();
    private final Label text = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final HBox actions = new HBox(8);

    Notice(Kind kind) {
        super(14);
        getStyleClass().addAll("notice", kind == Kind.WARNING ? "notice-warning" : "notice-wait");
        SVGPath icon = new SVGPath();
        icon.setContent(kind == Kind.WARNING ? WARNING_ICON : CLOCK_ICON);
        icon.getStyleClass().add("notice-icon");

        title.getStyleClass().add("notice-title");
        // Sin título (solo texto), que no ocupe sitio
        title.managedProperty().bind(title.textProperty().isNotEmpty());
        title.visibleProperty().bind(title.managedProperty());
        text.getStyleClass().add("text2");
        text.setWrapText(true);
        progress.getStyleClass().add("thin-bar");
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);
        VBox.setMargin(actions, new Insets(6, 0, 0, 0));

        VBox texts = new VBox(6, title, text, progress, actions);
        HBox.setHgrow(texts, Priority.ALWAYS);
        setAlignment(Pos.TOP_LEFT);
        getChildren().addAll(icon, texts);
    }

    /**
     * Aviso naranja para un error al hablar con VNDB, con un botón "Reintentar".
     * Lo usan la búsqueda y "Actualizar desde VNDB" de la ficha.
     */
    static Notice vndbError(Throwable error, Runnable retry) {
        Notice notice = new Notice(Kind.WARNING);
        if (error instanceof VndbException ve && ve.statusCode() == 0) {
            // statusCode 0 = ni siquiera hubo respuesta: sin conexión
            notice.title("No se puede conectar con VNDB")
                    .text("Tu biblioteca sigue funcionando sin conexión. Solo la búsqueda y "
                            + "«Actualizar desde VNDB» necesitan internet.");
        } else {
            notice.title("VNDB ha respondido con un error").text(error.getMessage());
        }
        return notice.action("Reintentar", retry);
    }

    Notice title(String value) {
        title.setText(value);
        return this;
    }

    Notice text(String value) {
        text.setText(value);
        return this;
    }

    /** Muestra la barra de progreso (0..1). */
    Notice progress(double value) {
        progress.setVisible(true);
        progress.setManaged(true);
        progress.setProgress(value);
        return this;
    }

    Notice action(String label, Runnable onClick) {
        Button button = new Button(label);
        button.setOnAction(event -> onClick.run());
        actions.getChildren().add(button);
        return this;
    }
}

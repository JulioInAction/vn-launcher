package com.vnlauncher.ui;

import com.vnlauncher.util.DisplayPaths;
import com.vnlauncher.util.ErrorDetails;
import javafx.animation.PauseTransition;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.nio.file.Path;

/**
 * Diálogo «Algo ha fallado» (error-dialog-*.png): qué ha pasado, qué hacer, «Detalles técnicos»
 * desplegables y los botones «Abrir carpeta de registros», «Copiar detalles» y «Cerrar».
 *
 * <p>Los detalles empiezan plegados: a quien no le interesan no le asusta una traza; quien informa
 * del problema los despliega o, directamente, los copia.
 */
final class ErrorDialog {

    private static final String TITLE = "Algo ha fallado";
    private static final String DETAILS = "Detalles técnicos";
    private static final String COPIED = "✓  Copiado";
    private static final String COPY = "Copiar detalles";
    private static final String NO_LOG = "(no disponible)";
    /** Chevron hacia la derecha (plegado); girado 90° = hacia abajo (desplegado). */
    private static final String CHEVRON = "M5 3 L9 7 L5 11";

    private ErrorDialog() {
    }

    /**
     * Muestra el diálogo y espera a que se cierre.
     *
     * @param owner   nodo de la ventana principal, o null si aún no hay ventana (error al arrancar)
     * @param message qué ha fallado, frase completa
     * @param advice  qué puede hacer el usuario, frase completa
     */
    static void show(Node owner, String message, String advice, Throwable error) {
        Path logFile = ErrorReporter.logFile();
        String details = ErrorDetails.format(error,
                logFile == null ? NO_LOG : DisplayPaths.forDisplay(logFile));

        ModalDialog dialog = new ModalDialog(owner, 600)
                .title(TITLE, true)
                .text(message)
                .text(advice);

        // --- Detalles técnicos, plegados
        TextArea detailsArea = new TextArea(details);
        detailsArea.setEditable(false);
        // Sin saltos de línea: partida, una línea de la traza («at clase.método(...)») se lee peor
        detailsArea.setWrapText(false);
        detailsArea.setPrefRowCount(7);
        detailsArea.getStyleClass().addAll("mono-box", "error-details");
        detailsArea.setVisible(false);
        detailsArea.setManaged(false);

        SVGPath chevron = new SVGPath();
        chevron.setContent(CHEVRON);
        chevron.getStyleClass().add("details-chevron");
        Button toggle = new Button(DETAILS, new Group(chevron));
        toggle.getStyleClass().addAll("accent-link", "details-toggle");
        toggle.setOnAction(event -> {
            boolean show = !detailsArea.isVisible();
            detailsArea.setVisible(show);
            detailsArea.setManaged(show);
            chevron.setRotate(show ? 90 : 0);
            // El diálogo es una ventana propia: hay que ajustarla al nuevo contenido
            detailsArea.getScene().getWindow().sizeToScene();
        });
        dialog.content(toggle).content(detailsArea);

        // --- Botones. Los dos primeros NO cierran el diálogo: se sustituye su acción
        Button openLogs = dialog.button("logs", "Abrir carpeta de registros", ModalDialog.ButtonKind.SECONDARY);
        openLogs.setOnAction(event -> ErrorReporter.openLogsFolder());
        openLogs.setDisable(logFile == null);
        Button copy = dialog.button("copy", COPY, ModalDialog.ButtonKind.SECONDARY);
        PauseTransition resetCopy = new PauseTransition(Duration.seconds(2));
        resetCopy.setOnFinished(event -> copy.setText(COPY));
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(details);
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText(COPIED);
            resetCopy.playFromStart();
        });
        dialog.button("close", "Cerrar", ModalDialog.ButtonKind.PRIMARY);
        dialog.showAndWait();
    }
}

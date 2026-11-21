package com.vnlauncher.ui;

import com.vnlauncher.model.ProfileManifest;
import com.vnlauncher.model.ProfilePreview;
import com.vnlauncher.util.Formats;
import com.vnlauncher.util.PlayTime;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.text.MessageFormat;

/**
 * «Importar perfil» (docs/diseño/capturas/importar-perfil-*.png): el resumen del perfil abierto y
 * el aviso de que sustituye al actual. Solo pregunta; importar lo hace SettingsController.
 */
final class ImportProfileDialog {

    static final String TITLE = "Importar perfil";
    /** «Exportado el 21/11/2026 desde VN Launcher 1.0.0» */
    static final String EXPORTED = "Exportado el %s desde VN Launcher %s";
    /**
     * MessageFormat con {@code choice}: «(1 novela)» pero «(8 novelas)» o «(0 novelas)». Así la
     * frase entera es una sola plantilla (traducible) en vez de pegar «novela» o «novelas».
     */
    static final String REPLACES = "Se sustituirá tu perfil actual ({0,choice,0#0 novelas|1#1 novela"
            + "|1<{0,number,integer} novelas}). Antes se guarda una copia automática que puedes restaurar "
            + "desde Ajustes. La aplicación se reiniciará para aplicar el perfil.";
    static final String EXE_NOTE = "Las rutas de los ejecutables son de cada ordenador: si alguna no existe "
            + "en este, la novela aparecerá marcada como «No encontrado».";
    static final String IMPORT = "Importar perfil";

    private ImportProfileDialog() {
    }

    /** @return true si el usuario pulsa «Importar perfil» */
    static boolean confirm(Node owner, ProfilePreview preview) {
        ProfileManifest manifest = preview.manifest();
        ModalDialog dialog = new ModalDialog(owner, 600).title(TITLE, false);

        Label file = new Label(preview.fileName());
        file.getStyleClass().add("mono-text");

        Label exported = new Label(String.format(EXPORTED, Formats.DATE.format(preview.exportedAt()),
                manifest.appVersion()));
        exported.getStyleClass().add("meta");
        // Cuatro columnas iguales como en el diseño; «Estanterías» llega con la fase 6
        GridPane stats = new GridPane();
        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            stats.getColumnConstraints().add(column);
        }
        stats.add(stat("Novelas", String.valueOf(manifest.novels())), 0, 0);
        stats.add(stat("Rutas", String.valueOf(manifest.routes())), 1, 0);
        stats.add(stat("Jugado", PlayTime.formatTotal(manifest.playtimeSeconds())), 2, 0);
        VBox summary = new VBox(12, exported, stats);
        summary.getStyleClass().add("profile-summary");

        Notice warning = new Notice(Notice.Kind.WARNING)
                .text(MessageFormat.format(REPLACES, preview.currentNovels()));
        warning.getStyleClass().add("compact");

        Label note = new Label(EXE_NOTE);
        note.getStyleClass().add("meta");
        note.setWrapText(true);

        dialog.content(file).content(summary).content(warning).content(note);
        dialog.button("cancel", "Cancelar", ModalDialog.ButtonKind.SECONDARY);
        dialog.button("import", IMPORT, ModalDialog.ButtonKind.PRIMARY);
        return "import".equals(dialog.showAndWait());
    }

    private static VBox stat(String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("stat-label");
        Label number = new Label(value);
        number.getStyleClass().add("stat-value");
        return new VBox(2, name, number);
    }
}

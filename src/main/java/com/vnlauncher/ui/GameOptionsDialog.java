package com.vnlauncher.ui;

import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.service.GameFiles;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * «Opciones del juego» (docs/design/mockups/game-options-*.png), con el engranaje de la ficha:
 * el ejecutable, el programa de configuración y Locale Emulator. Cada cambio se guarda al momento
 * (lo hace la ficha a través de {@link Actions}); «Listo» solo cierra.
 *
 * <p>Mientras está abierto, la ficha le pasa la VN cada vez que cambia ({@link #update}), así que
 * lo que se ve siempre es lo guardado.
 */
final class GameOptionsDialog {

    /** Lo que hace cada control. {@code owner} es un nodo del diálogo, para anclar lo que se abra. */
    interface Actions {
        void chooseExecutable(Node owner);

        void chooseConfig(Node owner);

        void removeConfig();

        void setLocaleEmulator(boolean use, Node owner);
    }

    static final String TITLE = "Opciones del juego";
    static final String EXECUTABLE = "Ejecutable";
    static final String CONFIG = "Programa de configuración";
    static final String CONFIG_HELP = "Opcional. Muchas novelas traen un config.exe aparte para la resolución, "
            + "el texto o el sonido.";
    static final String LOCALE_EMULATOR = "Lanzar con Locale Emulator";
    static final String LOCALE_EMULATOR_HELP = "Perfil japonés. Actívalo si el juego muestra caracteres "
            + "extraños o no arranca.";
    static final String NOT_SET = "Sin configurar";
    static final String NO_EXECUTABLE = "Sin elegir";
    static final String CHANGE = "Cambiar…";
    static final String CHOOSE = "Elegir…";
    static final String REMOVE = "Quitar";
    static final String DONE = "Listo";
    static final String FILE_MISSING = "No se encuentra este archivo";

    private final ModalDialog dialog;
    private final Actions actions;
    private final VBox exeField = new VBox(6);
    private final VBox configField = new VBox(6);
    private final Switch localeEmulator = new Switch();
    /** true mientras se pinta lo guardado: ese cambio del interruptor no hay que guardarlo. */
    private boolean updating;

    GameOptionsDialog(Node owner, LibraryEntry entry, GameFiles files, Actions actions) {
        this.actions = actions;
        dialog = new ModalDialog(owner, 590).titleWithSubtitle(TITLE, entry.vn().title());

        Label configHelp = new Label(CONFIG_HELP);
        configHelp.getStyleClass().add("form-help");
        configHelp.setWrapText(true);

        Label leTitle = new Label(LOCALE_EMULATOR);
        leTitle.getStyleClass().add("field-label");
        Label leHelp = new Label(LOCALE_EMULATOR_HELP);
        leHelp.getStyleClass().add("form-help");
        leHelp.setWrapText(true);
        VBox leTexts = new VBox(4, leTitle, leHelp);
        HBox.setHgrow(leTexts, Priority.ALWAYS);
        HBox leRow = new HBox(16, leTexts, localeEmulator);
        leRow.setAlignment(Pos.CENTER_LEFT);
        localeEmulator.selectedProperty().addListener((obs, was, now) -> {
            if (!updating) {
                actions.setLocaleEmulator(now, localeEmulator);
            }
        });

        // Las tres secciones más separadas entre sí que las líneas de cada una
        VBox sections = new VBox(22, field(EXECUTABLE, exeField), field(CONFIG, configField, configHelp), leRow);
        dialog.content(sections);
        Button done = dialog.button("done", DONE, ModalDialog.ButtonKind.PRIMARY);
        // El foco empieza en «Listo» (si no, lo coge la × y sale marcada)
        javafx.application.Platform.runLater(done::requestFocus);
        update(entry, files);
    }

    /** Abre el diálogo y espera a que se cierre. */
    void showAndWait() {
        dialog.showAndWait();
    }

    /** Lo cierra desde fuera (p. ej. para ir a Ajustes). */
    void close() {
        dialog.dismiss();
    }

    /**
     * Vuelve a pintar con la VN tal y como está guardada. Un archivo que ya no está se marca con
     * borde -vn-danger y «No se encuentra este archivo» debajo.
     */
    void update(LibraryEntry entry, GameFiles files) {
        // --- Ejecutable: la ruta + «Cambiar…», o «Sin elegir» + «Elegir…»
        if (entry.hasExecutable()) {
            exeField.getChildren().setAll(pathRow(entry.exePath(),
                    button(CHANGE, () -> actions.chooseExecutable(exeField))));
            markMissing(exeField, files.exeMissing());
        } else {
            exeField.getChildren().setAll(emptyRow(NO_EXECUTABLE, () -> actions.chooseExecutable(exeField)));
        }
        // --- Programa de configuración: la ruta + «Cambiar…» y «Quitar», o «Sin configurar» + «Elegir…»
        if (entry.hasConfigExecutable()) {
            configField.getChildren().setAll(pathRow(entry.configExePath(),
                    button(CHANGE, () -> actions.chooseConfig(configField)),
                    button(REMOVE, actions::removeConfig)));
            markMissing(configField, files.configMissing());
        } else {
            configField.getChildren().setAll(emptyRow(NOT_SET, () -> actions.chooseConfig(configField)));
        }
        updating = true;
        localeEmulator.setSelected(entry.useLocaleEmulator());
        updating = false;
    }

    /** Borde rojo en el campo de la ruta y «No se encuentra este archivo» debajo. */
    private static void markMissing(VBox field, boolean missing) {
        if (!missing) {
            return;
        }
        HBox row = (HBox) field.getChildren().getFirst();
        row.getChildren().getFirst().getStyleClass().add("missing");
        Label message = new Label(FILE_MISSING);
        message.getStyleClass().add("form-error");
        field.getChildren().add(message);
    }

    /**
     * Campo de solo lectura con la ruta. Si no cabe se recorta por el PRINCIPIO (lo que distingue
     * un juego de otro suele estar al final: la carpeta y el .exe); entera, en el tooltip.
     */
    private static HBox pathRow(String path, Button... buttons) {
        Label field = new Label(path);
        field.getStyleClass().addAll("path-field", "mono");
        field.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setMinWidth(0);
        field.setTooltip(new Tooltip(path));
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox row = new HBox(10, field);
        row.getChildren().addAll(buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Caja de borde discontinuo con «Sin configurar» y el enlace «Elegir…» dentro. */
    private static HBox emptyRow(String text, Runnable choose) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Button link = new Button(CHOOSE);
        link.getStyleClass().add("accent-link");
        link.setOnAction(event -> choose.run());
        HBox row = new HBox(10, label, grow, link);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("path-field-empty");
        return row;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static VBox field(String label, Node... nodes) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(8, l);
        box.getChildren().addAll(nodes);
        return box;
    }
}

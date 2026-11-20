package com.vnlauncher.ui;

import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.VnCharacter;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Diálogo para crear o editar una ruta. Devuelve un {@link Result} si el usuario pulsa Guardar,
 * o nada si cancela.
 *
 * <p>Se construye en código en vez de con FXML porque es pequeño y su contenido depende de los
 * datos (lista de personajes, ruta a editar).
 */
class RouteDialog extends Dialog<RouteDialog.Result> {

    /** Lo que ha rellenado el usuario. characterId es null si eligió "Sin heroína". */
    record Result(String name, String characterId, RouteStatus status, String notes) {
    }

    private final TextField nameField = new TextField();
    private final ComboBox<VnCharacter> characterBox = new ComboBox<>();
    private final ComboBox<RouteStatus> statusBox = new ComboBox<>();
    private final TextArea notesArea = new TextArea();

    /**
     * @param existing   la ruta a editar, o null para crear una nueva
     * @param characters personajes que se pueden elegir como heroína
     */
    RouteDialog(Node owner, Route existing, List<VnCharacter> characters) {
        setTitle(existing == null ? "Nueva ruta" : "Editar ruta");
        setHeaderText(existing == null ? "Añadir una ruta" : "Editar \"" + existing.name() + "\"");
        Dialogs.prepare(this, owner);

        // --- Heroína: la primera opción es null = "sin heroína" ---
        List<VnCharacter> options = new ArrayList<>();
        options.add(null);
        options.addAll(characters);
        characterBox.getItems().setAll(options);
        // Cómo se dibuja cada opción (en la lista desplegable y en el propio botón del ComboBox)
        characterBox.setCellFactory(list -> new CharacterCell());
        characterBox.setButtonCell(new CharacterCell());
        characterBox.setMaxWidth(Double.MAX_VALUE);

        // Al elegir heroína, si el nombre está vacío, proponer el suyo
        characterBox.valueProperty().addListener((obs, oldValue, selected) -> {
            boolean nameUntouched = nameField.getText().isBlank()
                    || (oldValue != null && nameField.getText().equals(oldValue.character().name()));
            if (selected != null && nameUntouched) {
                nameField.setText(selected.character().name());
            }
        });

        // --- Estado ---
        statusBox.getItems().setAll(RouteStatus.values());
        statusBox.setCellFactory(list -> new StatusCell());
        statusBox.setButtonCell(new StatusCell());

        notesArea.setPromptText("Requisitos, elecciones clave, finales...");
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(5);
        nameField.setPromptText("p. ej. Ruta de Nagisa, True End...");

        // Valores iniciales
        if (existing == null) {
            characterBox.setValue(null);
            statusBox.setValue(RouteStatus.PENDING);
        } else {
            nameField.setText(existing.name());
            characterBox.setValue(characters.stream()
                    .filter(vc -> vc.character().id().equals(existing.characterId()))
                    .findFirst().orElse(null));
            statusBox.setValue(existing.status());
            notesArea.setText(existing.notes() == null ? "" : existing.notes());
        }

        // --- Formulario en rejilla: etiquetas a la izquierda, campos a la derecha ---
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.addRow(0, formLabel("Heroína:"), characterBox);
        grid.addRow(1, formLabel("Nombre:"), nameField);
        grid.addRow(2, formLabel("Estado:"), statusBox);
        grid.addRow(3, formLabel("Notas:"), notesArea);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        grid.setPrefWidth(460);
        getDialogPane().setContent(grid);

        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        // El botón Guardar se desactiva mientras el nombre esté vacío. Es un "binding": JavaFX
        // recalcula la condición automáticamente cada vez que cambia el texto.
        getDialogPane().lookupButton(save).disableProperty().bind(
                Bindings.createBooleanBinding(() -> nameField.getText().isBlank(), nameField.textProperty()));

        // Convierte el botón pulsado en el resultado del diálogo
        setResultConverter(button -> button == save
                ? new Result(nameField.getText(),
                        characterBox.getValue() == null ? null : characterBox.getValue().character().id(),
                        statusBox.getValue(), notesArea.getText())
                : null);

        setOnShown(event -> nameField.requestFocus());
    }

    /**
     * Etiqueta que nunca se encoge por debajo de su texto. Sin esto, el GridPane le quita ancho
     * para dárselo a los campos y la etiqueta acaba mostrando "...".
     */
    private static Label formLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    /** Celda del desplegable de heroínas. */
    private static class CharacterCell extends ListCell<VnCharacter> {
        @Override
        protected void updateItem(VnCharacter vc, boolean empty) {
            super.updateItem(vc, empty);
            if (empty) {
                setText(null);
            } else if (vc == null) {
                setText("— Sin heroína (ruta común, true end...) —");
            } else {
                setText(vc.character().name()
                        + (vc.character().original() == null ? "" : "  (" + vc.character().original() + ")"));
            }
        }
    }

    /** Celda del desplegable de estados: muestra "En curso" en vez de "IN_PROGRESS". */
    static class StatusCell extends ListCell<RouteStatus> {
        @Override
        protected void updateItem(RouteStatus status, boolean empty) {
            super.updateItem(status, empty);
            setText(empty || status == null ? null : status.label());
        }
    }
}

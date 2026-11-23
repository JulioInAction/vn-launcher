package com.vnlauncher.ui;

import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.ShelfColor;
import com.vnlauncher.service.ShelfService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Crear o editar una estantería (docs/design/mockups/shelf-dialog-*.png): nombre, uno de
 * los 8 colores y cómo se verá en la barra lateral. Al editar, además, «Subir» y «Bajar» para
 * cambiarla de sitio sin arrastrar, y «Eliminar estantería» abajo a la izquierda.
 *
 * <p>Solo pregunta: quien lo abre guarda lo que devuelve (ver SidebarController).
 */
final class ShelfDialog {

    /** Qué hacer al cerrar. {@code position} solo cambia al editar (Subir/Bajar). */
    record Result(boolean delete, String name, ShelfColor color, int position) {
    }

    static final String NEW_TITLE = "Nueva estantería";
    static final String EDIT_TITLE = "Editar estantería";
    static final String CREATE = "Crear estantería";
    static final String SAVE = "Guardar";
    static final String DELETE = "Eliminar estantería";
    static final String PREVIEW = "Así se verá en la barra lateral";
    /** «Posición 2 de 3 en la barra lateral» */
    static final String POSITION = "Posición %d de %d en la barra lateral";

    private ShelfDialog() {
    }

    /** «+ Nueva estantería»: nombre vacío y el primer color que no tenga ninguna. */
    static Optional<Result> create(Node owner, List<Shelf> existing) {
        ShelfColor unused = java.util.Arrays.stream(ShelfColor.values())
                .filter(c -> existing.stream().noneMatch(s -> s.color() == c))
                .findFirst().orElse(ShelfColor.PINK);
        return show(owner, null, existing, "", unused);
    }

    /** «Editar…» en el menú de una estantería de la barra lateral. */
    static Optional<Result> edit(Node owner, Shelf shelf, List<Shelf> existing) {
        return show(owner, shelf, existing, shelf.name(), shelf.color());
    }

    private static Optional<Result> show(Node owner, Shelf editing, List<Shelf> existing, String name,
                                         ShelfColor color) {
        // --- Nombre: como mucho 40 caracteres (el TextFormatter rechaza lo que pase de ahí)
        TextField nameField = new TextField(name);
        nameField.setPromptText("p. ej. Favoritas");
        nameField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= ShelfService.MAX_NAME ? change : null));
        Label problem = new Label();
        problem.getStyleClass().add("form-error");
        problem.managedProperty().bind(problem.textProperty().isNotEmpty());

        // --- Color: 8 muestras; siempre hay una elegida
        ToggleGroup colors = new ToggleGroup();
        HBox swatches = new HBox(10);
        for (ShelfColor c : ShelfColor.values()) {
            ToggleButton swatch = new ToggleButton(null, colorSquare(c, "shelf-swatch-color"));
            swatch.getStyleClass().setAll("shelf-swatch");
            swatch.setToggleGroup(colors);
            swatch.setUserData(c);
            swatch.setTooltip(new Tooltip(c.label()));
            swatch.setSelected(c == color);
            swatches.getChildren().add(swatch);
        }
        // Pulsar la elegida la "deselecciona": se impide volviendo a seleccionarla
        colors.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });

        // --- Vista previa: la fila tal y como saldrá en la barra
        Label previewName = new Label();
        previewName.textProperty().bind(Bindings.createStringBinding(
                () -> nameField.getText().isBlank() ? NEW_TITLE : nameField.getText().strip(),
                nameField.textProperty()));
        Region previewSquare = colorSquare(color, "shelf-color");
        colors.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now != null) {
                previewSquare.getStyleClass().removeIf(cls -> cls.startsWith("shelf-") && !cls.equals("shelf-color"));
                previewSquare.getStyleClass().add(((ShelfColor) now.getUserData()).styleClass());
            }
        });
        Label previewCount = new Label(String.valueOf(editing == null ? 0 : editing.novels()));
        previewCount.getStyleClass().add("shelf-count");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox preview = new HBox(14, previewSquare, previewName, grow, previewCount);
        preview.setAlignment(Pos.CENTER_LEFT);
        preview.getStyleClass().add("shelf-preview");
        Label previewLabel = new Label(PREVIEW);
        previewLabel.getStyleClass().add("form-hint");

        ModalDialog dialog = new ModalDialog(owner, 560)
                .titleWithClose(editing == null ? NEW_TITLE : EDIT_TITLE)
                .content(field("Nombre", nameField, problem))
                .content(field("Color", swatches))
                .content(new VBox(8, previewLabel, preview));

        // --- Al editar: cambiarla de sitio sin arrastrar (se guarda al pulsar «Guardar»)
        int[] position = {editing == null ? existing.size() : editing.position()};
        if (editing != null && existing.size() > 1) {
            Label where = new Label();
            where.getStyleClass().add("text2");
            Button up = new Button("↑  Subir");
            Button down = new Button("↓  Bajar");
            up.getStyleClass().add("small-button");
            down.getStyleClass().add("small-button");
            Runnable refresh = () -> {
                where.setText(String.format(POSITION, position[0] + 1, existing.size()));
                up.setDisable(position[0] == 0);
                down.setDisable(position[0] == existing.size() - 1);
            };
            up.setOnAction(e -> {
                position[0]--;
                refresh.run();
            });
            down.setOnAction(e -> {
                position[0]++;
                refresh.run();
            });
            refresh.run();
            Region space = new Region();
            HBox.setHgrow(space, Priority.ALWAYS);
            HBox order = new HBox(8, where, space, up, down);
            order.setAlignment(Pos.CENTER_LEFT);
            dialog.content(order);
        }
        // «Eliminar» siempre al editar, aunque sea la única (Subir/Bajar solo tienen sentido con dos o más)
        if (editing != null) {
            dialog.button("delete", DELETE, ModalDialog.ButtonKind.DANGER);
        }
        dialog.button("cancel", "Cancelar", ModalDialog.ButtonKind.SECONDARY);
        Button primary = dialog.button("save", editing == null ? CREATE : SAVE, ModalDialog.ButtonKind.PRIMARY);

        // Mientras se escribe: el problema del nombre debajo (salvo «vacío», que solo desactiva)
        Runnable validate = () -> {
            Optional<String> issue = ShelfService.nameProblem(nameField.getText(), existing, editing);
            primary.setDisable(issue.isPresent());
            problem.setText(nameField.getText().isBlank() ? "" : issue.orElse(""));
        };
        nameField.textProperty().addListener((obs, old, now) -> validate.run());
        validate.run();
        Platform.runLater(nameField::requestFocus);

        String pressed = dialog.showAndWait();
        if ("delete".equals(pressed)) {
            return Optional.of(new Result(true, null, null, position[0]));
        }
        if (!"save".equals(pressed)) {
            return Optional.empty();
        }
        ShelfColor chosen = (ShelfColor) colors.getSelectedToggle().getUserData();
        return Optional.of(new Result(false, nameField.getText().strip(), chosen, position[0]));
    }

    /** Cuadradito del color de una estantería (barra lateral, chips, muestras). */
    static Region colorSquare(ShelfColor color, String styleClass) {
        Region square = new Region();
        square.getStyleClass().addAll(styleClass, color.styleClass());
        return square;
    }

    private static VBox field(String label, Node... nodes) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(8, l);
        box.getChildren().addAll(nodes);
        return box;
    }
}

package com.vnlauncher.ui;

import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteStatus;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Diálogo para crear o editar una ruta (docs/diseño/capturas/dialogo-ruta.png): nombre, heroína
 * (tarjetas de radio con avatar, más "Sin heroína"), estado, orden recomendado y notas. Al editar,
 * "Eliminar ruta" abajo a la izquierda.
 */
final class RouteDialog {

    /**
     * Lo que ha elegido el usuario. characterId es null si eligió "Sin heroína"; position empieza
     * en 1. Si delete es true, pulsó "Eliminar ruta" (el resto de campos no importan).
     */
    record Result(String name, String characterId, RouteStatus status, String notes, int position,
                  boolean delete) {
    }

    private static final String TRASH_ICON = "M5 7H19 M10 11V17 M14 11V17 M6 7L7 20H17L18 7 M9 7V4H15V7";
    private static final double AVATAR = 34;

    private RouteDialog() {
    }

    /**
     * @param existing   ruta a editar, o null para crear una
     * @param characters personajes que se pueden elegir como heroína
     * @param routeCount número de rutas que hay ahora (para el desplegable del orden)
     */
    static Optional<Result> show(Node owner, String vnTitle, Route existing, List<VnCharacter> characters,
                                 int routeCount, ImageCacheService images, ContentFilter contentFilter) {
        // --- Nombre
        TextField nameField = new TextField(existing == null ? "" : existing.name());
        nameField.setPromptText("p. ej. Unlimited Blade Works, True End...");

        // --- Heroína: un RadioButton por personaje, con su avatar, todos en el mismo ToggleGroup
        ToggleGroup heroineGroup = new ToggleGroup();
        TilePane heroines = new TilePane(10, 10);
        heroines.setPrefColumns(3);
        // 3 × 214 + 2 huecos de 10 = 662: el ancho útil del diálogo (720 menos 28 de margen por lado)
        heroines.setPrefTileWidth(214);
        for (VnCharacter vc : characters) {
            GameCharacter c = vc.character();
            Path file = c.imagePath() == null ? null : images.resolve(c.imagePath());
            heroines.getChildren().add(heroineCard(heroineGroup, vc, c.name(),
                    CoverView.avatar(file, contentFilter.isSensitive(c.imageSexual(), c.imageViolence()),
                            AVATAR, c.name())));
        }
        Region emptyAvatar = new Region();
        emptyAvatar.getStyleClass().add("avatar-empty");
        emptyAvatar.setMinSize(AVATAR, AVATAR);
        RadioButton none = heroineCard(heroineGroup, null, "Sin heroína", emptyAvatar);
        heroines.getChildren().add(none);
        // Selección inicial: la heroína de la ruta, o "Sin heroína"
        heroineGroup.selectToggle(none);
        if (existing != null && existing.characterId() != null) {
            heroineGroup.getToggles().stream()
                    .filter(t -> t.getUserData() instanceof VnCharacter vc
                            && vc.character().id().equals(existing.characterId()))
                    .findFirst().ifPresent(heroineGroup::selectToggle);
        }
        // Al elegir heroína, si el nombre está vacío (o era el de la anterior), proponer el suyo
        heroineGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                heroineGroup.selectToggle(oldToggle); // siempre una elegida
                return;
            }
            String oldName = oldToggle != null && oldToggle.getUserData() instanceof VnCharacter o
                    ? o.character().name() : "";
            boolean untouched = nameField.getText().isBlank() || nameField.getText().equals(oldName);
            if (untouched && newToggle.getUserData() instanceof VnCharacter vc) {
                nameField.setText(vc.character().name());
            }
        });
        Label heroineHint = new Label("Son los personajes «primary» de VNDB. Elige «Sin heroína» para la "
                + "ruta común o un True End.");
        heroineHint.getStyleClass().addAll("meta", "small");
        heroineHint.setWrapText(true);

        // --- Estado y orden recomendado
        Segmented<RouteStatus> status = new Segmented<>(Arrays.asList(RouteStatus.values()), RouteStatus::label);
        status.setValue(existing == null ? RouteStatus.PENDING : existing.status());

        int positions = existing == null ? routeCount + 1 : routeCount;
        ComboBox<Integer> order = new ComboBox<>();
        order.getItems().setAll(IntStream.rangeClosed(1, Math.max(1, positions)).boxed().toList());
        order.setCellFactory(list -> new OrdinalCell());
        order.setButtonCell(new OrdinalCell());
        order.setValue(existing == null ? positions : existing.recommendedOrder());
        order.setPrefWidth(140);

        HBox statusRow = new HBox(24, field("Estado", status), field("Orden recomendado", order));

        // --- Notas
        TextArea notes = new TextArea(existing == null || existing.notes() == null ? "" : existing.notes());
        notes.setPromptText("Requisitos, elecciones clave, finales...");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);

        // 720: con tres tarjetas de heroína por fila, nombres como "Fujibayashi" caben sin partirse
        ModalDialog dialog = new ModalDialog(owner, 720)
                .titleWithSubtitle(existing == null ? "Nueva ruta" : "Editar ruta", vnTitle)
                .content(field("Nombre de la ruta", nameField))
                .content(field("Heroína", heroines, heroineHint))
                .content(statusRow)
                .content(field("Notas", notes));
        if (existing != null) {
            Button delete = dialog.button("delete", "Eliminar ruta", ModalDialog.ButtonKind.DANGER);
            SVGPath trash = new SVGPath();
            trash.setContent(TRASH_ICON);
            trash.getStyleClass().add("icon-danger-small");
            delete.setGraphic(trash);
        }
        dialog.button("cancel", "Cancelar", ModalDialog.ButtonKind.SECONDARY);
        Button save = dialog.button("save", "Guardar", ModalDialog.ButtonKind.PRIMARY);
        // "Guardar" desactivado mientras el nombre esté vacío. Es un "binding": JavaFX recalcula la
        // condición automáticamente cada vez que cambia el texto.
        save.disableProperty().bind(
                Bindings.createBooleanBinding(() -> nameField.getText().isBlank(), nameField.textProperty()));
        javafx.application.Platform.runLater(nameField::requestFocus);

        String pressed = dialog.showAndWait();
        if ("delete".equals(pressed)) {
            return Optional.of(new Result(null, null, null, null, 0, true));
        }
        if (!"save".equals(pressed)) {
            return Optional.empty();
        }
        VnCharacter heroine = heroineGroup.getSelectedToggle().getUserData() instanceof VnCharacter vc ? vc : null;
        return Optional.of(new Result(nameField.getText(),
                heroine == null ? null : heroine.character().id(),
                status.getValue(), notes.getText(), order.getValue(), false));
    }

    /** Tarjeta de radio: (o) [avatar] Nombre. userData = el personaje (null = sin heroína). */
    private static RadioButton heroineCard(ToggleGroup group, VnCharacter vc, String name, Node avatar) {
        RadioButton card = new RadioButton(name);
        card.setGraphic(avatar);
        card.setGraphicTextGap(10);
        card.setWrapText(true);
        card.setToggleGroup(group);
        card.setUserData(vc);
        card.getStyleClass().add("heroine-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Etiqueta encima del campo (y textos de ayuda opcionales debajo). */
    private static VBox field(String label, Node... nodes) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(8, l);
        box.getChildren().addAll(nodes);
        return box;
    }

    /** "1.º", "2.º"... */
    private static class OrdinalCell extends ListCell<Integer> {
        @Override
        protected void updateItem(Integer n, boolean empty) {
            super.updateItem(n, empty);
            setText(empty || n == null ? null : n + ".º");
        }
    }
}

package com.vnlauncher.ui;

import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.VnCharacter;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;

/**
 * Tarjeta de un personaje: imagen, nombre, nombre original, etiqueta de spoiler si lo es,
 * y abajo "+ Tiene ruta" (o "✓ Con ruta" si ya tiene). Al pasar el ratón muestra la descripción.
 * Si la imagen es sensible, un clic la muestra/oculta.
 */
class CharacterCard extends VBox {

    static final double IMAGE_WIDTH = 120;
    static final double IMAGE_HEIGHT = 170;

    private boolean revealed = false;

    /**
     * @param imageFile  imagen en disco ya resuelta (o null)
     * @param sensitive  si la imagen debe difuminarse
     * @param hasRoute   si el usuario ya ha confirmado que tiene ruta
     * @param onAddRoute qué hacer al pulsar "+ Tiene ruta"
     */
    CharacterCard(VnCharacter vc, Path imageFile, boolean sensitive, boolean hasRoute, Runnable onAddRoute) {
        super(4);
        GameCharacter c = vc.character();
        getStyleClass().add("character-card");
        setMaxWidth(IMAGE_WIDTH);

        CoverView image = new CoverView(imageFile, sensitive, IMAGE_WIDTH, IMAGE_HEIGHT);
        if (sensitive) {
            image.setCursor(Cursor.HAND);
            image.setOnMouseClicked(event -> {
                revealed = !revealed;
                image.setRevealed(revealed);
            });
        }
        getChildren().add(image);

        Label name = new Label(c.name());
        name.getStyleClass().add("character-name");
        name.setWrapText(true);
        name.setMaxWidth(IMAGE_WIDTH);
        getChildren().add(name);

        if (c.original() != null) {
            Label original = new Label(c.original());
            original.getStyleClass().add("muted");
            original.setMaxWidth(IMAGE_WIDTH);
            getChildren().add(original);
        }

        if (vc.isSpoiler()) {
            Label spoiler = new Label(vc.spoilerLevel() >= 2 ? "Spoiler mayor" : "Spoiler menor");
            spoiler.getStyleClass().add("spoiler-badge");
            getChildren().add(spoiler);
        }

        if (hasRoute) {
            Label badge = new Label("✓ Con ruta");
            badge.getStyleClass().add("route-badge");
            getChildren().add(badge);
        } else {
            Button add = new Button("+ Tiene ruta");
            add.getStyleClass().add("small-button");
            add.setTooltip(new Tooltip("Confirmar que este personaje tiene ruta (se crea como Pendiente)"));
            add.setOnAction(event -> {
                add.setDisable(true); // evitar doble clic mientras se guarda
                onAddRoute.run();
            });
            getChildren().add(add);
        }

        if (c.description() != null && !c.description().isBlank()) {
            Tooltip tooltip = new Tooltip(c.description());
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(420);
            tooltip.setShowDelay(Duration.millis(400));
            tooltip.setShowDuration(Duration.INDEFINITE); // que no desaparezca mientras se lee
            Tooltip.install(image, tooltip);
        }
    }
}

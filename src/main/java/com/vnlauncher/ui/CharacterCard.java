package com.vnlauncher.ui;

import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.VnCharacter;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;

/**
 * Tarjeta de un personaje: imagen, nombre, nombre original y, si es spoiler, una etiqueta.
 * Al pasar el ratón muestra la descripción. Si la imagen es sensible, un clic la muestra/oculta.
 */
class CharacterCard extends VBox {

    static final double IMAGE_WIDTH = 120;
    static final double IMAGE_HEIGHT = 170;

    private boolean revealed = false;

    /**
     * @param imageFile imagen en disco ya resuelta (o null)
     * @param sensitive si la imagen debe difuminarse
     */
    CharacterCard(VnCharacter vc, Path imageFile, boolean sensitive) {
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

        if (c.description() != null && !c.description().isBlank()) {
            Tooltip tooltip = new Tooltip(c.description());
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(420);
            tooltip.setShowDelay(Duration.millis(400));
            tooltip.setShowDuration(Duration.INDEFINITE); // que no desaparezca mientras se lee
            Tooltip.install(this, tooltip);
        }
    }
}

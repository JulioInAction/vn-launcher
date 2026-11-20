package com.vnlauncher.ui;

import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.VnCharacter;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;

/**
 * Tarjeta de un personaje: imagen (o iniciales), nombre y abajo "Tiene ruta" o el botón
 * "Marcar con ruta". Al pasar el ratón por la imagen se ve la descripción.
 *
 * <p>Si el personaje es spoiler y está oculto, se dibuja una tarjeta tapada con
 * "Oculto por spoiler" y un botón "Mostrar", sin nombre ni imagen.
 */
class CharacterCard extends VBox {

    static final double IMAGE_WIDTH = 124;
    static final double IMAGE_HEIGHT = 170;

    /** Qué se debe mostrar en la tarjeta; lo decide VnDetailController. */
    record Options(boolean hidden, boolean sensitive, boolean revealSensitive, boolean hasRoute) {
    }

    private boolean revealed;

    /**
     * @param imageFile    imagen en disco ya resuelta (o null)
     * @param onAddRoute   qué hacer al pulsar "Marcar con ruta"
     * @param onRevealSpoiler qué hacer al pulsar "Mostrar" en una tarjeta oculta
     */
    CharacterCard(VnCharacter vc, Path imageFile, Options options, Runnable onAddRoute, Runnable onRevealSpoiler) {
        super(6);
        GameCharacter c = vc.character();
        getStyleClass().add("character-card");
        setPrefWidth(IMAGE_WIDTH + 16);
        setMaxWidth(IMAGE_WIDTH + 16);

        if (options.hidden()) {
            getChildren().addAll(hiddenPlaceholder(onRevealSpoiler), nameLabel("Personaje oculto"));
        } else {
            CoverView image = new CoverView(imageFile, options.sensitive(), IMAGE_WIDTH, IMAGE_HEIGHT, c.name());
            revealed = options.revealSensitive();
            image.setRevealed(revealed);
            if (options.sensitive()) {
                image.setCursor(Cursor.HAND);
                image.setOnMouseClicked(event -> {
                    revealed = !revealed;
                    image.setRevealed(revealed);
                });
            }
            if (c.description() != null && !c.description().isBlank()) {
                Tooltip tooltip = new Tooltip(c.description());
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(420);
                tooltip.setShowDelay(Duration.millis(400));
                tooltip.setShowDuration(Duration.INDEFINITE); // que no desaparezca mientras se lee
                Tooltip.install(image, tooltip);
            }
            getChildren().addAll(image, nameLabel(c.name()));
            if (c.original() != null) {
                Label original = new Label(c.original());
                original.getStyleClass().addAll("muted", "small");
                original.setMaxWidth(IMAGE_WIDTH);
                getChildren().add(original);
            }
            if (vc.isSpoiler()) {
                getChildren().add(pill(vc.spoilerLevel() >= 2 ? "Spoiler mayor" : "Spoiler menor", "pill-spoiler"));
            }
        }

        if (options.hasRoute()) {
            getChildren().add(pill("Tiene ruta", "pill-route"));
        } else {
            Button add = new Button("Marcar con ruta");
            add.getStyleClass().add("small-button");
            add.setMaxWidth(Double.MAX_VALUE);
            add.setTooltip(new Tooltip("Confirmar que este personaje tiene ruta (se crea como Pendiente)"));
            add.setOnAction(event -> {
                add.setDisable(true); // evitar doble clic mientras se guarda
                onAddRoute.run();
            });
            getChildren().add(add);
        }
    }

    private StackPane hiddenPlaceholder(Runnable onReveal) {
        Label text = new Label("Oculto por spoiler");
        text.getStyleClass().add("small");
        Button show = new Button("Mostrar");
        show.getStyleClass().add("small-button");
        show.setOnAction(event -> onReveal.run());
        VBox box = new VBox(10, text, show);
        box.setAlignment(Pos.CENTER);
        StackPane placeholder = new StackPane(box);
        placeholder.getStyleClass().add("spoiler-placeholder");
        placeholder.setMinSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        placeholder.setPrefSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        return placeholder;
    }

    private static Label nameLabel(String text) {
        Label name = new Label(text);
        name.getStyleClass().add("character-name");
        name.setWrapText(true);
        name.setMaxWidth(IMAGE_WIDTH);
        return name;
    }

    private static Label pill(String text, String styleClass) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("pill", styleClass);
        return pill;
    }
}

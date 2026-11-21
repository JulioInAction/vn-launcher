package com.vnlauncher.ui;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * Ventana modal con el aspecto de DISEÑO.md ("Diálogos"): tarjeta con esquinas de 16 px y sombra,
 * título (con icono opcional), contenido y botones abajo; la acción principal en rosa a la derecha
 * del todo y la destructiva ("Eliminar ruta") abajo a la izquierda. Detrás, la ventana principal
 * queda tapada con -vn-backdrop.
 *
 * <p>Uso: crear, añadir contenido y botones, y llamar a {@link #showAndWait()}, que devuelve el
 * identificador del botón pulsado (o null si se cerró con Esc o la ×).
 *
 * <p>Es un {@link Stage} propio y no un Dialog de JavaFX porque así controlamos todo el aspecto:
 * StageStyle.TRANSPARENT quita el marco de Windows y deja ver las esquinas redondeadas.
 */
class ModalDialog {

    /** Tipos de botón: dónde va y qué estilo tiene. */
    /**
     * Tipos de botón: dónde va y qué estilo tiene. DANGER y LINK van abajo a la izquierda, como
     * enlaces (rojo para "Eliminar ruta", color de acento para "Volver a la de VNDB").
     */
    enum ButtonKind { PRIMARY, SECONDARY, DANGER, LINK }

    private static final String ERROR_ICON = "M12 3A9 9 0 1 0 12.01 3Z M12 7.5V13 M12 16.4V16.6";

    private final Node owner;
    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final VBox card = new VBox(16);
    private final HBox header = new HBox(12);
    private final VBox body = new VBox(12);
    private final HBox leftButtons = new HBox(8);
    private final HBox rightButtons = new HBox(10);
    private String result;

    /**
     * @param owner nodo de la ventana sobre la que se abre (para centrarlo y taparla)
     * @param width ancho de la tarjeta
     */
    ModalDialog(Node owner, double width) {
        this.owner = owner;
        card.getStyleClass().add("modal-card");
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        header.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(leftButtons, spacer, rightButtons);
        footer.setAlignment(Pos.CENTER);
        card.getChildren().addAll(header, body, footer);

        // Sombra en una región propia detrás de la tarjeta (mismo tamaño): así el efecto no toca
        // al texto, que conserva el suavizado LCD
        Region shadow = new Region();
        shadow.getStyleClass().add("modal-shadow");
        StackPane cardWithShadow = new StackPane(shadow, card);
        cardWithShadow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // El margen alrededor de la tarjeta deja sitio a la sombra (si no, se recortaría)
        StackPane root = new StackPane(cardWithShadow);
        root.getStyleClass().add("modal-root");
        Scene scene = new Scene(root);
        // Color.TRANSPARENT no es un color del diseño: es "sin fondo", para que solo se vea la tarjeta
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Views.class.getResource("styles.css").toExternalForm());
        Themes.apply(root, Themes.current(owner));
        // Esc = cerrar sin elegir nada. Con un filtro (se ejecuta ANTES que los controles): si no,
        // un campo de texto con el foco se queda la tecla y el diálogo no se cierra
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                close(null);
            }
        });
        stage.setScene(scene);
        if (owner != null && owner.getScene() != null) {
            stage.initOwner(owner.getScene().getWindow());
            // WINDOW_MODAL: bloquea la ventana principal mientras el diálogo está abierto
            stage.initModality(Modality.WINDOW_MODAL);
        }
    }

    /** Título; con {@code danger} se pone delante el icono de error en -vn-danger. */
    ModalDialog title(String text, boolean danger) {
        if (danger) {
            SVGPath icon = new SVGPath();
            icon.setContent(ERROR_ICON);
            icon.getStyleClass().add("icon-danger");
            header.getChildren().add(icon);
        }
        Label title = new Label(text);
        title.getStyleClass().add("modal-title");
        title.setWrapText(true);
        header.getChildren().add(title);
        return this;
    }

    /** Título con botón × para cerrar (estanteria-dialogo-*.png). */
    ModalDialog titleWithClose(String text) {
        return titleWithSubtitle(text, null);
    }

    /** Título con subtítulo debajo (p. ej. el nombre de la VN; null = sin subtítulo) y botón × para cerrar. */
    ModalDialog titleWithSubtitle(String text, String subtitle) {
        Label title = new Label(text);
        title.getStyleClass().add("modal-title");
        VBox texts = new VBox(2, title);
        if (subtitle != null) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("meta");
            texts.getChildren().add(sub);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().add("modal-close");
        close.setOnAction(event -> close(null));
        header.getChildren().addAll(texts, spacer, close);
        header.setAlignment(Pos.TOP_LEFT);
        return this;
    }

    /** Párrafo de texto normal. */
    ModalDialog text(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text2");
        label.setWrapText(true);
        body.getChildren().add(label);
        return this;
    }

    /** Cualquier otro contenido (campos, rutas de archivo...). */
    ModalDialog content(Node node) {
        body.getChildren().add(node);
        return this;
    }

    /**
     * Añade un botón. Al pulsarlo, el diálogo se cierra y showAndWait() devuelve {@code id}.
     * PRIMARY va a la derecha del todo; DANGER, abajo a la izquierda.
     */
    Button button(String id, String text, ButtonKind kind) {
        Button button = new Button(text);
        button.setOnAction(event -> close(id));
        switch (kind) {
            case PRIMARY -> {
                button.getStyleClass().add("primary-button");
                button.setDefaultButton(true); // Enter lo pulsa
                rightButtons.getChildren().add(button);
            }
            case SECONDARY -> {
                button.setCancelButton("cancel".equals(id));
                // Los secundarios van a la izquierda del principal
                rightButtons.getChildren().add(Math.max(0, rightButtons.getChildren().size()
                        - (hasPrimary() ? 1 : 0)), button);
            }
            case DANGER -> {
                button.getStyleClass().add("danger-link");
                leftButtons.getChildren().add(button);
            }
            case LINK -> {
                button.getStyleClass().add("accent-link");
                leftButtons.getChildren().add(button);
            }
        }
        return button;
    }

    private boolean hasPrimary() {
        return rightButtons.getChildren().stream().anyMatch(n -> n.getStyleClass().contains("primary-button"));
    }

    /** Muestra el diálogo y espera. Devuelve el id del botón pulsado, o null si se cerró sin elegir. */
    String showAndWait() {
        Region backdrop = showBackdrop();
        centerOnOwner();
        stage.showAndWait();
        hideBackdrop(backdrop);
        return result;
    }

    /**
     * Centra el diálogo sobre la ventana de la app. Un Stage, a diferencia de un Dialog de JavaFX,
     * se centra en la PANTALLA: con la ventana a un lado (o en otro monitor) quedaría descentrado.
     * El tamaño se calcula antes de mostrarlo (applyCss + prefWidth/prefHeight) para que no "salte".
     * Coordenadas redondeadas: una ventana a medio píxel podría verse borrosa.
     */
    private void centerOnOwner() {
        if (owner == null || owner.getScene() == null || owner.getScene().getWindow() == null) {
            return; // sin dueño: que JavaFX la centre en la pantalla
        }
        Window window = owner.getScene().getWindow();
        Parent root = stage.getScene().getRoot();
        root.applyCss();
        double width = root.prefWidth(-1);
        double height = root.prefHeight(width);
        double x = window.getX() + (window.getWidth() - width) / 2;
        double y = window.getY() + (window.getHeight() - height) / 2;
        // Si la ventana es más pequeña que el diálogo, centrado se saldría de la pantalla: se mete
        // dentro de la zona útil de la pantalla donde está la ventana
        Rectangle2D screen = Screen.getScreensForRectangle(window.getX(), window.getY(),
                        window.getWidth(), window.getHeight()).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        x = Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - width));
        y = Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - height));
        stage.setX(Math.round(x));
        stage.setY(Math.round(y));
    }

    /** Cierra el diálogo desde fuera, como si se pulsara la × (showAndWait devuelve null). */
    void dismiss() {
        close(null);
    }

    private void close(String id) {
        result = id;
        stage.close();
    }

    /**
     * Tapa la ventana principal con -vn-backdrop mientras el diálogo está abierto. Solo es posible si
     * la raíz de esa ventana es un Pane (la de VN Launcher es un StackPane, ver main-view.fxml).
     */
    private Region showBackdrop() {
        if (owner == null || owner.getScene() == null) {
            return null;
        }
        Parent root = owner.getScene().getRoot();
        if (!(root instanceof Pane pane)) {
            return null;
        }
        Region backdrop = new Region();
        backdrop.getStyleClass().add("dialog-backdrop");
        backdrop.setManaged(false); // no altera el layout de la ventana
        backdrop.resizeRelocate(0, 0, pane.getWidth(), pane.getHeight());
        pane.getChildren().add(backdrop);
        return backdrop;
    }

    private void hideBackdrop(Region backdrop) {
        if (backdrop != null && backdrop.getParent() instanceof Pane pane) {
            pane.getChildren().remove(backdrop);
        }
    }
}

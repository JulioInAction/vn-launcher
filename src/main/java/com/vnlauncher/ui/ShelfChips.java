package com.vnlauncher.ui;

import com.vnlauncher.model.Shelf;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Las estanterías de una VN en su ficha (ficha-estado-*.png): un chip por estantería con su color y
 * una × para sacarla, y «+ Añadir a estantería», que abre la lista de todas con una casilla (marcada
 * si ya está) y «+ Nueva estantería…» al final.
 */
final class ShelfChips extends FlowPane {

    static final String ADD = "+ Añadir a estantería";
    static final String NEW_SHELF = "+   Nueva estantería…";
    /** «Sacar de Favoritas» */
    static final String REMOVE_TOOLTIP = "Sacar de «%s»";

    private final Consumer<Shelf> onAdd;
    private final Consumer<Shelf> onRemove;
    private final Runnable onNewShelf;
    private final ContextMenu menu = new ContextMenu();

    /**
     * @param onAdd      meter la VN en una estantería
     * @param onRemove   sacarla de una
     * @param onNewShelf crear una estantería nueva y meterla en ella
     */
    ShelfChips(Consumer<Shelf> onAdd, Consumer<Shelf> onRemove, Runnable onNewShelf) {
        super(8, 8);
        this.onAdd = onAdd;
        this.onRemove = onRemove;
        this.onNewShelf = onNewShelf;
        setAlignment(Pos.CENTER_LEFT);
        menu.getStyleClass().add("status-menu"); // mismo aspecto que el selector de estado
    }

    /**
     * @param mine las estanterías en las que está la VN
     * @param all  todas, para el menú de «+ Añadir a estantería»
     */
    void setShelves(List<Shelf> mine, List<Shelf> all) {
        getChildren().clear();
        for (Shelf shelf : mine) {
            getChildren().add(chip(shelf));
        }
        Button add = new Button(ADD);
        add.getStyleClass().add("add-shelf-chip");
        add.setOnAction(event -> openMenu(add, mine, all));
        getChildren().add(add);
    }

    private HBox chip(Shelf shelf) {
        Label name = new Label(shelf.name());
        Button remove = new Button("✕");
        remove.getStyleClass().add("chip-remove");
        remove.setTooltip(new Tooltip(String.format(REMOVE_TOOLTIP, shelf.name())));
        remove.setOnAction(event -> onRemove.accept(shelf));
        HBox chip = new HBox(8, ShelfDialog.colorSquare(shelf.color(), "shelf-color"), name, remove);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("shelf-chip");
        return chip;
    }

    private void openMenu(Button anchor, List<Shelf> mine, List<Shelf> all) {
        menu.getItems().clear();
        for (Shelf shelf : all) {
            boolean in = mine.stream().anyMatch(s -> s.id() == shelf.id());
            // La casilla solo enseña el estado: el clic lo recibe la fila entera
            CheckBox check = new CheckBox();
            check.setSelected(in);
            check.getStyleClass().add("menu-check");
            check.setMouseTransparent(true);
            check.setFocusTraversable(false);
            HBox row = new HBox(10, check, ShelfDialog.colorSquare(shelf.color(), "shelf-color"),
                    new Label(shelf.name()));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinWidth(260);
            CustomMenuItem item = new CustomMenuItem(row);
            item.setOnAction(event -> (in ? onRemove : onAdd).accept(shelf));
            menu.getItems().add(item);
        }
        if (!all.isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }
        MenuItem create = new MenuItem(NEW_SHELF);
        create.getStyleClass().add("accent-item");
        create.setOnAction(event -> onNewShelf.run());
        menu.getItems().add(create);
        menu.show(anchor, Side.BOTTOM, 0, 6);
    }
}

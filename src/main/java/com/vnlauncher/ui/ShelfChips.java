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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Las estanterías de una VN en su ficha (ficha-simplificada-*.png): un chip por estantería con su
 * color y una × para sacarla, y un «+» redondo que abre la lista de todas con una casilla (marcada
 * si ya está) y «+ Nueva estantería…» al final.
 *
 * <p>No es un nodo propio: pone sus chips en la fila que le dan, detrás de lo que ya haya en ella
 * (el selector de estado), para que todo salte de línea junto si no cabe.
 */
final class ShelfChips {

    static final String ADD_TOOLTIP = "Añadir a estantería";
    static final String NEW_SHELF = "+   Nueva estantería…";
    /** «Sacar de Favoritas» */
    static final String REMOVE_TOOLTIP = "Sacar de «%s»";

    private final FlowPane row;
    /** Cuántos nodos de la fila son de otro (van delante y no se tocan). */
    private final int fixed;
    private final Consumer<Shelf> onAdd;
    private final Consumer<Shelf> onRemove;
    private final Runnable onNewShelf;
    private final ContextMenu menu = new ContextMenu();

    /**
     * @param row        la fila de chips de la ficha; lo que ya tenga se queda delante
     * @param onAdd      meter la VN en una estantería
     * @param onRemove   sacarla de una
     * @param onNewShelf crear una estantería nueva y meterla en ella
     */
    ShelfChips(FlowPane row, Consumer<Shelf> onAdd, Consumer<Shelf> onRemove, Runnable onNewShelf) {
        this.row = row;
        this.fixed = row.getChildren().size();
        this.onAdd = onAdd;
        this.onRemove = onRemove;
        this.onNewShelf = onNewShelf;
        menu.getStyleClass().add("status-menu"); // mismo aspecto que el selector de estado
    }

    /**
     * @param mine las estanterías en las que está la VN
     * @param all  todas, para el menú de «+ Añadir a estantería»
     */
    void setShelves(List<Shelf> mine, List<Shelf> all) {
        row.getChildren().remove(fixed, row.getChildren().size());
        for (Shelf shelf : mine) {
            row.getChildren().add(chip(shelf));
        }
        // «+» redondo de 34 px con borde discontinuo
        Button add = new Button("+");
        add.getStyleClass().add("add-shelf-round");
        add.setTooltip(new Tooltip(ADD_TOOLTIP));
        add.setOnAction(event -> openMenu(add, mine, all));
        row.getChildren().add(add);
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
        menu.getItems().setAll(menuItems(mine, all, onAdd, onRemove, onNewShelf));
        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    /**
     * Las filas de la lista: cada estantería con una casilla (marcada si la VN ya está) y, al final,
     * «+ Nueva estantería…». Las usa también el menú de clic derecho de las portadas.
     */
    static List<MenuItem> menuItems(List<Shelf> mine, List<Shelf> all, Consumer<Shelf> onAdd,
                                    Consumer<Shelf> onRemove, Runnable onNewShelf) {
        List<MenuItem> items = new ArrayList<>();
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
            items.add(item);
        }
        if (!all.isEmpty()) {
            items.add(new SeparatorMenuItem());
        }
        MenuItem create = new MenuItem(NEW_SHELF);
        create.getStyleClass().add("accent-item");
        create.setOnAction(event -> onNewShelf.run());
        items.add(create);
        return items;
    }
}

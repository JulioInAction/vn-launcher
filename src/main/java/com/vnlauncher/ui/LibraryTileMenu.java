package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.LibraryItem;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.Shelf;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menú de clic derecho de una portada de la biblioteca (fase 9): lo que se hace a menudo sin abrir
 * la ficha.
 * <ul>
 *   <li>Jugar (o «Elegir ejecutable…» si aún no tiene; desactivado si está abierto o si su
 *       ejecutable ya no está);</li>
 *   <li>Abrir configuración del juego, solo si tiene programa de configuración (desactivado si
 *       ya no está);</li>
 *   <li>Abrir carpeta del juego (desactivado sin ejecutable o si ya no está);</li>
 *   <li>Añadir a estantería y Cambiar estado, con las mismas listas que la ficha;</li>
 *   <li>Quitar de la biblioteca, con confirmación (LibraryRemoval).</li>
 * </ul>
 * Lo que hace cada opción ya está probado en los servicios; esta clase solo lo junta.
 */
final class LibraryTileMenu {

    static final String PLAY = "Jugar";
    static final String PLAYING = "Jugando…";
    static final String CHOOSE_EXE = "Elegir ejecutable…";
    static final String OPEN_CONFIG = "Abrir configuración del juego";
    static final String OPEN_FOLDER = "Abrir carpeta del juego";
    static final String ADD_TO_SHELF = "Añadir a estantería";
    static final String CHANGE_STATUS = "Cambiar estado";
    static final String REMOVE = "Quitar de la biblioteca";

    /** Las estanterías, leídas al abrir el menú (cambian desde la barra lateral). */
    private record Shelves(List<Shelf> mine, List<Shelf> all) {
    }

    private final AppContext ctx;
    private final Navigator navigator;
    private final Consumer<String> urlOpener;
    private final LibraryItem item;
    /** Tras cualquier cambio: volver a cargar la biblioteca y la barra lateral. */
    private final Runnable onChanged;

    LibraryTileMenu(AppContext ctx, Navigator navigator, Consumer<String> urlOpener, LibraryItem item,
                    Runnable onChanged) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.urlOpener = urlOpener;
        this.item = item;
        this.onChanged = onChanged;
    }

    /**
     * Lee las estanterías en segundo plano y abre el menú donde se hizo clic.
     *
     * @param tile la tarjeta de la portada
     */
    void show(Node tile, double screenX, double screenY) {
        String vnId = item.entry().vn().id();
        BackgroundTasks.run(ctx.background(),
                () -> new Shelves(ctx.shelfService().shelvesOf(vnId), ctx.shelfService().findAll()),
                shelves -> {
                    if (tile.getScene() == null) {
                        return; // la biblioteca se ha vuelto a pintar mientras tanto
                    }
                    build(tile.getScene().getRoot(), shelves).show(tile, screenX, screenY);
                },
                "No se han podido leer las estanterías.");
    }

    /** @param owner la raíz de la ventana: los diálogos se anclan a ella, no a la tarjeta */
    private ContextMenu build(Node owner, Shelves shelves) {
        LibraryEntry entry = item.entry();
        String vnId = entry.vn().id();
        String title = entry.vn().title();
        LaunchFlow launchFlow = new LaunchFlow(ctx, navigator, owner);
        boolean running = ctx.launchService().isRunning(vnId);

        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("tile-menu");

        // --- Jugar
        MenuItem play;
        if (running) {
            play = new MenuItem(PLAYING);
            play.setDisable(true);
        } else if (!entry.hasExecutable()) {
            play = item(CHOOSE_EXE, () -> launchFlow.chooseExecutable(vnId, title, null, onChanged));
        } else {
            play = item(PLAY, () -> launchFlow.play(vnId, title, onChanged, onChanged));
            play.setDisable(item.exeMissing());
        }
        menu.getItems().add(play);

        // --- Abrir configuración del juego, justo debajo y solo si tiene
        if (entry.hasConfigExecutable()) {
            MenuItem config = item(OPEN_CONFIG, () -> launchFlow.openConfig(vnId, title, () -> { }, onChanged));
            config.setDisable(item.configMissing());
            menu.getItems().add(config);
        }

        // --- Abrir carpeta del juego
        MenuItem folder = item(OPEN_FOLDER, () -> openFolder(entry.exePath()));
        folder.setDisable(!entry.hasExecutable() || item.exeMissing());
        menu.getItems().add(folder);

        // --- Estanterías y estado: las mismas listas que en la ficha, como submenús
        Menu shelfMenu = new Menu(ADD_TO_SHELF);
        shelfMenu.getItems().setAll(ShelfChips.menuItems(shelves.mine(), shelves.all(),
                shelf -> save(() -> ctx.shelfService().addVn(shelf.id(), vnId)),
                shelf -> save(() -> ctx.shelfService().removeVn(shelf.id(), vnId)),
                () -> ShelfDialog.create(owner, shelves.all()).ifPresent(result -> save(() -> {
                    Shelf created = ctx.shelfService().create(result.name(), result.color());
                    ctx.shelfService().addVn(created.id(), vnId);
                }))));
        Menu statusMenu = new Menu(CHANGE_STATUS);
        statusMenu.getItems().setAll(StatusPicker.menuItems(entry.statusOverride(), item.automaticStatus(),
                status -> save(() -> ctx.libraryService().setStatus(vnId, status))));

        // --- Quitar, separado y en rojo
        MenuItem remove = item(REMOVE, () -> LibraryRemoval.confirmAndRemove(ctx, owner, entry,
                () -> { }, onChanged, () -> { }));
        remove.getStyleClass().add("danger-item");

        menu.getItems().addAll(new SeparatorMenuItem(), shelfMenu, statusMenu, new SeparatorMenuItem(), remove);
        return menu;
    }

    /** Abre en el explorador la carpeta donde está el ejecutable. */
    private void openFolder(String exePath) {
        try {
            Path folder = Path.of(exePath).getParent();
            if (folder != null) {
                urlOpener.accept(folder.toUri().toString());
            }
        } catch (InvalidPathException e) {
            // No puede pasar: la opción está desactivada si la ruta no lleva a un archivo
        }
    }

    /** Guarda en segundo plano y, al terminar, vuelve a pintar la biblioteca y la barra lateral. */
    private void save(SqlWork work) {
        BackgroundTasks.run(ctx.background(),
                () -> {
                    work.run();
                    return null;
                },
                done -> onChanged.run(),
                "No se ha podido guardar el cambio.");
    }

    /** Trabajo con la base de datos que puede lanzar SQLException (un Runnable normal no puede). */
    @FunctionalInterface
    private interface SqlWork {
        void run() throws java.sql.SQLException;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem menuItem = new MenuItem(text);
        menuItem.setOnAction(event -> action.run());
        return menuItem;
    }
}

package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.Theme;
import com.vnlauncher.service.PlaytimeTracker;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Controlador de la ventana principal (main-view.fxml): barra lateral común a la izquierda y la
 * pantalla actual en el centro. Implementa {@link Navigator} para que las demás vistas puedan
 * pedir cambiar de pantalla.
 */
public class MainController implements Navigator {

    private final AppContext context;
    /** Abre una URL en el navegador del sistema (viene de Application.getHostServices()). */
    private final Consumer<String> urlOpener;
    private final Theme initialTheme;

    @FXML private BorderPane root;

    private SidebarController sidebar;
    /** Controlador de la pantalla visible, para avisarle de cambios en el tiempo de juego. */
    private Object currentController;

    // Biblioteca y búsqueda se crean una sola vez y se reutilizan (la búsqueda conserva sus
    // resultados al volver). El detalle de una VN se crea nuevo cada vez.
    private Parent libraryView;
    private LibraryController libraryController;
    private Parent searchView;

    public MainController(AppContext context, Consumer<String> urlOpener, Theme initialTheme) {
        this.context = context;
        this.urlOpener = urlOpener;
        this.initialTheme = initialTheme;
    }

    /** FXMLLoader llama a initialize() automáticamente cuando ya ha rellenado los campos @FXML. */
    @FXML
    private void initialize() {
        // root es el nodo raíz de la escena: el tema se aplica poniéndole (o no) la clase tema-claro
        Themes.apply(root, initialTheme);
        sidebar = new SidebarController(context, this);
        root.setLeft(Views.load("sidebar.fxml", sidebar));
        sidebar.showTheme(initialTheme);
        listenToPlaytime();
        showLibrary();
    }

    /**
     * El PlaytimeTracker avisa cuando un juego se abre o se cierra. Esos avisos llegan desde SU
     * hilo, no desde el de la interfaz: Platform.runLater(...) encarga el trabajo al hilo de la
     * interfaz, que es el único que puede tocar los controles.
     */
    private void listenToPlaytime() {
        context.playtimeTracker().addListener(new PlaytimeTracker.Listener() {
            @Override
            public void sessionStarted(String vnId) {
                Platform.runLater(() -> notifyPlaytime(vnId));
            }

            @Override
            public void sessionEnded(String vnId, Duration played) {
                Platform.runLater(() -> notifyPlaytime(vnId));
            }
        });
    }

    private void notifyPlaytime(String vnId) {
        sidebar.refresh();
        // "instanceof con patrón" (Java 16+): comprueba el tipo y ya lo da convertido en "aware"
        if (currentController instanceof PlaytimeAware aware) {
            aware.playtimeChanged(vnId);
        }
    }

    @Override
    public void showLibrary() {
        showLibrary(null);
    }

    @Override
    public void showLibrary(PlayStatus shelf) {
        if (libraryView == null) {
            libraryController = new LibraryController(context, this);
            libraryView = Views.load("library-view.fxml", libraryController);
        }
        libraryController.show(shelf); // recarga: puede haber VNs nuevas añadidas desde la búsqueda
        root.setCenter(libraryView);
        currentController = libraryController;
        sidebar.setActive(SidebarController.Section.LIBRARY);
        sidebar.refresh();
    }

    @Override
    public void showSearch() {
        if (searchView == null) {
            searchView = Views.load("search-view.fxml", new SearchController(context, this));
        }
        root.setCenter(searchView);
        currentController = null;
        sidebar.setActive(SidebarController.Section.SEARCH);
    }

    /** La pantalla de Ajustes llega en la Fase 7; de momento, un aviso. */
    @Override
    public void showSettings() {
        Label title = new Label("Ajustes");
        title.getStyleClass().add("page-title");
        Label text = new Label("La pantalla de ajustes llegará en la Fase 7 (ruta de Locale Emulator, "
                + "difuminado de imágenes...). El tema ya se puede cambiar con el botón de abajo de la barra lateral.");
        text.getStyleClass().add("muted");
        text.setWrapText(true);
        VBox page = new VBox(16, title, text);
        page.getStyleClass().add("content");
        root.setCenter(page);
        currentController = null;
        sidebar.setActive(SidebarController.Section.SETTINGS);
    }

    @Override
    public void showVnDetail(String vnId) {
        VnDetailController detail = new VnDetailController(context, this, urlOpener, vnId);
        root.setCenter(Views.load("vn-detail-view.fxml", detail));
        currentController = detail;
        sidebar.setActive(SidebarController.Section.LIBRARY);
        sidebar.refresh();
    }

    @Override
    public void refreshSidebar() {
        sidebar.refresh();
    }
}

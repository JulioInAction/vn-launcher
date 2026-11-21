package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.VnTag;
import com.vnlauncher.model.Theme;
import com.vnlauncher.service.PlaytimeTracker;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.time.Duration;
import java.util.function.BooleanSupplier;
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

    @FXML private StackPane appRoot;
    @FXML private BorderPane root;

    private final BooleanSupplier relauncher;
    private SidebarController sidebar;
    private ThemeManager themeManager;
    /** Controlador de la pantalla visible, para avisarle de cambios en el tiempo de juego. */
    private Object currentController;

    // La biblioteca se crea una sola vez y se reutiliza (así recuerda sus filtros). La búsqueda y el
    // detalle de una VN se crean nuevos cada vez: al volver a «Buscar» se empieza de cero.
    private Parent libraryView;
    private LibraryController libraryController;
    /** Búsqueda en pantalla; null si se está en otra pestaña. */
    private Parent searchView;
    private SearchController searchController;

    /**
     * @param relauncher abre una VN Launcher nueva que espera a que se cierre esta (para restaurar una
     *                   copia); devuelve false si no ha podido
     */
    public MainController(AppContext context, Consumer<String> urlOpener, Theme initialTheme,
                          BooleanSupplier relauncher) {
        this.context = context;
        this.urlOpener = urlOpener;
        this.initialTheme = initialTheme;
        this.relauncher = relauncher;
    }

    /** FXMLLoader llama a initialize() automáticamente cuando ya ha rellenado los campos @FXML. */
    @FXML
    private void initialize() {
        // appRoot es el nodo raíz de la escena: el tema se aplica poniéndole (o no) la clase tema-claro
        themeManager = new ThemeManager(appRoot, context, initialTheme);
        sidebar = new SidebarController(context, this);
        root.setLeft(Views.load("sidebar.fxml", sidebar));
        sidebar.setThemeManager(themeManager);
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
        library().showAll(); // recarga: puede haber VNs nuevas añadidas desde la búsqueda
        displayLibrary();
    }

    @Override
    public void showLibrary(PlayStatus status) {
        library().showStatus(status);
        displayLibrary();
    }

    @Override
    public void showLibraryWithTag(VnTag tag) {
        library().showTag(tag);
        displayLibrary();
    }

    @Override
    public void backToLibrary() {
        library().reload(); // mismos filtros que antes de abrir la ficha
        displayLibrary();
    }

    /** La vista de la biblioteca se crea una vez y se reutiliza (así recuerda sus filtros). */
    private LibraryController library() {
        if (libraryView == null) {
            libraryController = new LibraryController(context, this);
            libraryView = Views.load("library-view.fxml", libraryController);
            // Al quitar el filtro de estado con su ✕, que la barra deje de resaltarlo
            libraryController.setOnStatusChanged(status -> sidebar.setActiveStatus(status));
        }
        return libraryController;
    }

    private void displayLibrary() {
        leaveSearch();
        root.setCenter(libraryView);
        currentController = libraryController;
        sidebar.setActive(SidebarController.Section.LIBRARY);
        sidebar.setActiveStatus(libraryController.status());
        sidebar.refresh();
    }

    /**
     * Abre «Buscar» vacía. Si ya se está en ella (p. ej. al pulsar otra vez «02 Buscar»), se deja
     * como está: solo se empieza de cero al venir de otra pestaña.
     */
    @Override
    public void showSearch() {
        if (searchView != null && root.getCenter() == searchView) {
            return;
        }
        searchController = new SearchController(context, this);
        searchView = Views.load("search-view.fxml", searchController);
        root.setCenter(searchView);
        currentController = null;
        sidebar.setActive(SidebarController.Section.SEARCH);
    }

    /**
     * Al salir de «Buscar» se descarta la búsqueda: sus resultados no se guardan para la próxima
     * vez, y se cancela lo que estuviera en marcha (si no, una búsqueda que espera al límite de
     * VNDB gastaría una petición para nada).
     */
    private void leaveSearch() {
        if (searchController != null) {
            searchController.close();
        }
        searchController = null;
        searchView = null;
    }

    @Override
    public void showSettings() {
        leaveSearch();
        SettingsController settings = new SettingsController(context, themeManager, urlOpener, relauncher);
        root.setCenter(Views.load("settings-view.fxml", settings));
        currentController = settings;
        sidebar.setActive(SidebarController.Section.SETTINGS);
    }

    @Override
    public void showVnDetail(String vnId) {
        leaveSearch();
        VnDetailController detail = new VnDetailController(context, this, urlOpener, vnId);
        root.setCenter(Views.load("vn-detail-view.fxml", detail));
        currentController = detail;
        sidebar.setActive(SidebarController.Section.LIBRARY);
        sidebar.setActiveStatus(libraryController.status());
        sidebar.refresh();
    }

    @Override
    public void refreshSidebar() {
        sidebar.refresh();
    }
}

package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.RouteProgress;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.util.PlayTime;
import com.vnlauncher.util.RelativeTime;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Ficha de una VN de la biblioteca: cabecera con acciones (jugar, elegir ejecutable,
 * Locale Emulator), datos (tiempo jugado, última partida, rutas), descripción, rutas y
 * personajes principales.
 *
 * <p>Recibe el AppContext entero en vez de cada servicio por separado porque usa casi todos;
 * con ocho parámetros en el constructor se volvía ilegible.
 */
public class VnDetailController implements PlaytimeAware {

    /** Descripción recortada a unos 4-5 renglones hasta pulsar "Leer más". */
    private static final int DESCRIPTION_PREVIEW_CHARS = 380;

    private final AppContext ctx;
    private final Navigator navigator;
    private final Consumer<String> urlOpener;
    private final String vnId;

    // --- cabecera
    @FXML private VBox noticeBox;
    @FXML private Hyperlink refreshLink;
    @FXML private Label refreshStatus;
    @FXML private StackPane coverBox;
    @FXML private Label titleLabel;
    @FXML private Label altTitleLabel;
    @FXML private Label metaLabel;
    @FXML private Button playButton;
    @FXML private Button exeButton;
    @FXML private Label exePathLabel;
    @FXML private Button configButton;
    @FXML private HBox configRow;
    @FXML private Label configPathLabel;
    @FXML private Hyperlink configChangeLink;
    @FXML private Hyperlink configRemoveLink;
    @FXML private CheckBox localeEmulatorCheck;
    @FXML private Label playTimeLabel;
    @FXML private Label lastPlayedLabel;
    @FXML private Label routesStatLabel;
    @FXML private ProgressBar routesStatBar;
    @FXML private Label launchStatusLabel;
    @FXML private Label descriptionLabel;
    @FXML private Hyperlink readMoreLink;
    @FXML private Button removeButton;
    // --- rutas y personajes
    @FXML private VBox routesContainer;
    @FXML private CheckBox showSpoilersCheck;
    @FXML private CheckBox showSensitiveCheck;
    @FXML private Label charactersStatus;
    @FXML private FlowPane charactersPane;

    private RoutesPane routesPane;
    private LaunchFlow launchFlow;

    private LibraryEntry entry;
    private boolean descriptionExpanded = false;
    /** true mientras se está lanzando (entre pulsar Jugar y que el juego arranque). */
    private boolean launching = false;
    /** Personajes cargados (todos, incluidos los spoiler); null mientras se cargan. */
    private List<VnCharacter> characters;
    /** Rutas actuales de la VN. */
    private List<Route> routes = List.of();
    /** Personajes spoiler que el usuario ha destapado uno a uno con "Mostrar". */
    private final Set<String> revealedSpoilers = new HashSet<>();

    public VnDetailController(AppContext ctx, Navigator navigator, Consumer<String> urlOpener, String vnId) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.urlOpener = urlOpener;
        this.vnId = vnId;
    }

    @FXML
    private void initialize() {
        launchFlow = new LaunchFlow(ctx, navigator, playButton);
        removeButton.setDisable(true);   // hasta que se cargue la VN
        playButton.setDisable(true);
        loadHeader();
        reloadPlayData(); // tiempo jugado (suma de sesiones)

        // La etiqueta de estado solo ocupa sitio cuando tiene texto (binding: se recalcula sola)
        launchStatusLabel.managedProperty().bind(launchStatusLabel.textProperty().isNotEmpty());
        // La zona de avisos, solo cuando tiene alguno
        noticeBox.managedProperty().bind(Bindings.isNotEmpty(noticeBox.getChildren()));
        // Guardar el cambio de Locale Emulator. Al pintar la VN también se cambia la casilla, pero
        // onLocaleEmulatorToggled ve que coincide con lo guardado y no hace nada.
        localeEmulatorCheck.selectedProperty().addListener(
                (obs, oldValue, selected) -> onLocaleEmulatorToggled(selected));

        localeEmulatorCheck.setTooltip(new Tooltip(
                "Lanza el juego con LEProc.exe -run (perfil por defecto de Locale Emulator, japonés).\n"
                        + "Útil para juegos japoneses que muestran texto ilegible o no arrancan."));

        // Rutas: un componente propio (RoutesPane) que avisa cuando cambian las rutas.
        routesPane = new RoutesPane(ctx.routeService(), ctx.background(), ctx.imageCache(),
                ctx.contentFilter(), vnId);
        routesPane.setOnRoutesChanged(changed -> {
            routes = changed;
            renderRouteStats();
            navigator.refreshSidebar(); // las estanterías dependen de las rutas
            renderCharacters(); // actualizar "Tiene ruta" / "Marcar con ruta"
        });
        routesContainer.getChildren().add(routesPane);
        routesPane.reload();

        // Personajes: se cargan a la vez, en otra tarea (la primera vez tardan: VNDB + imágenes).
        showSpoilersCheck.selectedProperty().addListener((obs, oldValue, selected) -> renderCharacters());
        // Valor inicial de "Mostrar spoilers": el de Ajustes → Contenido
        BackgroundTasks.run(ctx.background(), () -> ctx.settingsService().showSpoilers(),
                showSpoilersCheck::setSelected, error -> { });
        showSensitiveCheck.selectedProperty().addListener((obs, oldValue, selected) -> renderCharacters());
        loadCharacters();
    }

    /** Personajes: la primera vez tardan (VNDB + imágenes); después salen de la base de datos. */
    private void loadCharacters() {
        charactersStatus.setText("Cargando personajes...");
        charactersStatus.setManaged(true);
        BackgroundTasks.run(ctx.background(), () -> ctx.characterService().getPrimaryCharacters(vnId),
                loaded -> {
                    characters = loaded;
                    renderCharacters();
                },
                error -> charactersStatus.setText(
                        "No se pudieron cargar los personajes: " + error.getMessage()));
    }

    // =========================================================== cabecera

    /** Lee la VN (y la fecha de sus datos de VNDB) y pinta la cabecera. */
    private void loadHeader() {
        record Header(Optional<LibraryEntry> entry, Optional<Instant> fetchedAt) {
        }
        BackgroundTasks.run(ctx.background(),
                () -> new Header(ctx.libraryService().find(vnId), ctx.libraryService().fetchedAt(vnId)),
                header -> {
                    if (header.entry().isEmpty()) {
                        navigator.showLibrary(); // ya no está en la biblioteca
                        return;
                    }
                    renderVn(header.entry().get());
                    renderLaunchInfo(header.entry().get());
                    removeButton.setDisable(false);
                    String when = header.fetchedAt()
                            .map(t -> RelativeTime.format(t, Instant.now(), ZoneId.systemDefault()))
                            .orElse("nunca");
                    refreshLink.setTooltip(new Tooltip("Vuelve a pedir a VNDB la ficha y los personajes "
                            + "(2 peticiones).\nDatos descargados: " + when + "."));
                },
                error -> titleLabel.setText("Error: " + error.getMessage()));
    }

    /**
     * Portada (la elegida en la galería o la de VNDB) con su control «Cambiar portada» debajo, y
     * los datos de la VN que vienen de VNDB: títulos, desarrollador y descripción.
     */
    private void renderVn(LibraryEntry loaded) {
        VisualNovel vn = loaded.vn();
        CoverView cover = new CoverView(Covers.file(ctx, loaded), Covers.sensitive(ctx, loaded), 220, 330, vn.title());
        if (cover.isSensitive()) {
            // Clic en la portada para mostrarla u ocultarla. Usamos un array de 1 elemento porque
            // una lambda solo puede usar variables locales "efectivamente finales": no podemos
            // reasignar un boolean, pero sí cambiar el contenido del array.
            boolean[] revealed = {false};
            cover.setCursor(Cursor.HAND);
            Tooltip.install(cover, new Tooltip("Clic para mostrar u ocultar la imagen"));
            cover.setOnMouseClicked(event -> {
                revealed[0] = !revealed[0];
                cover.setRevealed(revealed[0]);
            });
        }
        // Debajo, a 6 px, el control para cambiarla: siempre ocupa sus 44 px (50 en total con la
        // separación), así que al abrirse no mueve nada
        CoverChanger changer = new CoverChanger(cover, 220, () -> CoverGallery.show(playButton, ctx, loaded, () -> {
            loadHeader();               // pinta la portada nueva
            navigator.refreshSidebar(); // y en «Recientes»
        }));
        coverBox.getChildren().setAll(new VBox(CoverChanger.GAP, cover, changer));

        titleLabel.setText(vn.title());
        routesPane.setVnTitle(vn.title());
        altTitleLabel.setText(vn.altTitle() == null ? "" : vn.altTitle());
        altTitleLabel.setManaged(vn.altTitle() != null);
        metaLabel.setText(SearchController.meta(vn));
        renderDescription(vn.description());
    }

    /** Parte de la cabecera que cambia al configurar o lanzar el juego. */
    private void renderLaunchInfo(LibraryEntry loaded) {
        entry = loaded;
        exeButton.setText(loaded.hasExecutable() ? "Cambiar ejecutable" : "Elegir ejecutable");
        exePathLabel.setText(loaded.hasExecutable() ? loaded.exePath()
                : "Aún no has elegido el ejecutable del juego.");
        localeEmulatorCheck.setSelected(loaded.useLocaleEmulator());
        renderConfigInfo(loaded);
        lastPlayedLabel.setText(RelativeTime.format(loaded.lastPlayedAt(), Instant.now(), ZoneId.systemDefault()));
        renderPlayButton();
    }

    /**
     * Programa de configuración: con él, botón "Configuración" y "Configuración: ruta · Cambiar ·
     * Quitar"; sin él, solo el enlace para añadirlo (y nada si aún no hay ni ejecutable del juego).
     */
    private void renderConfigInfo(LibraryEntry loaded) {
        boolean hasConfig = loaded.hasConfigExecutable();
        show(configButton, hasConfig);
        show(configRow, hasConfig || loaded.hasExecutable());
        show(configPathLabel, hasConfig);
        show(configRemoveLink, hasConfig);
        configPathLabel.setText(hasConfig ? "Configuración: " + loaded.configExePath() : "");
        configChangeLink.setText(hasConfig ? "Cambiar" : "+  Añadir programa de configuración (config.exe...)");
    }

    /** Muestra u oculta un nodo sin que ocupe sitio cuando está oculto. */
    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** "▶ Jugar", "Lanzando..." o "Jugando..." según el estado. */
    private void renderPlayButton() {
        boolean running = ctx.launchService().isRunning(vnId);
        playButton.setText(running ? "Jugando..." : launching ? "Lanzando..." : "▶  Jugar");
        playButton.setDisable(entry == null || running || launching);
    }

    /** Recarga los datos de juego: última partida y tiempo total (en segundo plano). */
    private void reloadPlayData() {
        // Un record local: solo existe dentro de este método, para devolver dos valores juntos
        record PlayData(Optional<LibraryEntry> entry, long seconds) {
        }
        BackgroundTasks.run(ctx.background(),
                () -> new PlayData(ctx.libraryService().find(vnId), ctx.libraryService().totalPlaySeconds(vnId)),
                data -> {
                    data.entry().ifPresent(this::renderLaunchInfo);
                    playTimeLabel.setText(PlayTime.format(data.seconds()));
                },
                error -> launchStatusLabel.setText("Error: " + error.getMessage()));
    }

    private void renderRouteStats() {
        RouteProgress progress = ctx.routeService().progressOf(routes);
        routesStatLabel.setText(progress.total() == 0 ? "Sin rutas"
                : progress.completed() + " de " + progress.total());
        routesStatBar.setProgress(progress.fraction());
    }

    private void renderDescription(String description) {
        if (description == null || description.isBlank()) {
            descriptionLabel.setText("Sin descripción.");
            readMoreLink.setVisible(false);
            readMoreLink.setManaged(false);
            return;
        }
        boolean longText = description.length() > DESCRIPTION_PREVIEW_CHARS;
        readMoreLink.setVisible(longText);
        readMoreLink.setManaged(longText);
        if (!longText || descriptionExpanded) {
            descriptionLabel.setText(description);
            readMoreLink.setText("Leer menos");
        } else {
            // Cortar en el último espacio antes del límite, para no partir una palabra
            String cut = description.substring(0, DESCRIPTION_PREVIEW_CHARS);
            int lastSpace = cut.lastIndexOf(' ');
            descriptionLabel.setText((lastSpace > 0 ? cut.substring(0, lastSpace) : cut) + "…");
            readMoreLink.setText("Leer más");
        }
    }

    @FXML
    private void onToggleDescription() {
        descriptionExpanded = !descriptionExpanded;
        renderDescription(entry.vn().description());
    }

    @FXML
    private void onOpenVndb() {
        urlOpener.accept("https://vndb.org/" + vnId);
    }

    /**
     * "Actualizar desde VNDB": vuelve a pedir la ficha y los personajes y repinta la pantalla.
     * Las rutas, el tiempo de juego y el ejecutable no cambian (son datos del usuario).
     */
    @FXML
    private void onRefresh() {
        noticeBox.getChildren().clear();
        refreshLink.setDisable(true);
        // Si ahora mismo hay que esperar por el límite de VNDB, decirlo (la tarea espera sola)
        Duration wait = ctx.rateLimiter().timeUntilAvailable();
        refreshStatus.setText(wait.isZero() ? "Actualizando..."
                : "Esperando al límite de VNDB (" + wait.toMinutesPart() + ":"
                        + String.format("%02d", wait.toSecondsPart()) + ")...");
        BackgroundTasks.run(ctx.background(), () -> ctx.refreshService().refresh(vnId),
                result -> {
                    refreshLink.setDisable(false);
                    if (!result.found()) {
                        refreshStatus.setText("");
                        noticeBox.getChildren().setAll(new Notice(Notice.Kind.WARNING)
                                .title("VNDB ya no tiene esta novela")
                                .text("Puede que la hayan borrado o unido a otra. Se conservan los "
                                        + "datos que ya tenías."));
                        return;
                    }
                    refreshStatus.setText("✓  Actualizado");
                    loadHeader();
                    loadCharacters();
                    navigator.refreshSidebar(); // la portada o el título pueden haber cambiado
                },
                error -> {
                    refreshLink.setDisable(false);
                    refreshStatus.setText("");
                    noticeBox.getChildren().setAll(Notice.vndbError(error, this::onRefresh));
                });
    }

    // =========================================================== lanzar el juego

    @FXML
    private void onPlay() {
        if (entry == null) {
            return;
        }
        launching = true;
        renderPlayButton();
        launchFlow.play(vnId, entry.vn().title(),
                () -> {
                    launching = false;
                    launchStatusLabel.setText("Juego lanzado. El tiempo se cuenta mientras siga abierto.");
                    reloadPlayData();   // última partida + botón "Jugando..."
                    navigator.refreshSidebar(); // "Recientes" y estantería "Jugando"
                },
                () -> {
                    launching = false;
                    reloadPlayData();   // puede haber cambiado el ejecutable o LE en el intento
                });
    }

    /** Un juego se ha abierto o cerrado (aviso de MainController). */
    @Override
    public void playtimeChanged(String changedVnId) {
        if (vnId.equals(changedVnId)) {
            if (!ctx.launchService().isRunning(vnId)) {
                launchStatusLabel.setText("");
            }
            reloadPlayData();
        }
    }

    @FXML
    private void onChooseExecutable() {
        launchFlow.chooseExecutable(vnId, entry.vn().title(), entry.exePath(), () -> {
            launchStatusLabel.setText("Ejecutable guardado.");
            reloadPlayData();
        });
    }

    @FXML
    private void onOpenConfig() {
        launchFlow.openConfig(vnId, entry.vn().title(),
                () -> launchStatusLabel.setText("Programa de configuración abierto."),
                this::reloadPlayData); // por si se eligió otro .exe en el diálogo de error
    }

    @FXML
    private void onChooseConfig() {
        // Empieza en la carpeta del programa actual o, si no hay, en la del juego
        String startFrom = entry.hasConfigExecutable() ? entry.configExePath() : entry.exePath();
        launchFlow.chooseConfig(vnId, entry.vn().title(), startFrom, () -> {
            launchStatusLabel.setText("Programa de configuración guardado.");
            reloadPlayData();
        });
    }

    @FXML
    private void onRemoveConfig() {
        // Solo se olvida la ruta; el archivo no se toca. Por eso no hace falta confirmar.
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setConfigExecutable(vnId, null);
                    return null;
                },
                ignored -> reloadPlayData(),
                error -> Dialogs.error(playButton, error.getMessage()));
    }

    private void onLocaleEmulatorToggled(boolean selected) {
        if (entry == null || selected == entry.useLocaleEmulator()) {
            return; // nada que guardar (p. ej. el cambio lo hizo renderLaunchInfo)
        }
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setUseLocaleEmulator(vnId, selected);
                    // Si se activa y no sabemos dónde está LEProc.exe, avisar ya
                    return selected && ctx.settingsService().localeEmulatorPath().isEmpty();
                },
                needsPath -> {
                    reloadPlayData();
                    if (needsPath) {
                        ModalDialog dialog = new ModalDialog(playButton, 480)
                                .title("Falta indicar dónde está Locale Emulator", false)
                                .text("Elige el archivo LEProc.exe en Ajustes → Locale Emulator. "
                                        + "Solo hay que hacerlo una vez para todas las novelas.");
                        dialog.button("later", "Más tarde", ModalDialog.ButtonKind.SECONDARY);
                        dialog.button("settings", "Ir a Ajustes", ModalDialog.ButtonKind.PRIMARY);
                        if ("settings".equals(dialog.showAndWait())) {
                            navigator.showSettings();
                        }
                    }
                },
                error -> Dialogs.error(playButton, error.getMessage()));
    }

    // =========================================================== personajes

    /**
     * Dibuja las tarjetas de personaje. Los spoiler aparecen tapados ("Oculto por spoiler")
     * salvo que esté marcado "Mostrar spoilers" o se hayan destapado uno a uno.
     */
    private void renderCharacters() {
        if (characters == null) {
            return;
        }
        boolean showSpoilers = showSpoilersCheck.isSelected();
        boolean showSensitive = showSensitiveCheck.isSelected();
        // Ids de los personajes que ya tienen alguna ruta (un Set para buscar rápido)
        Set<String> withRoute = routes.stream()
                .map(Route::characterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        charactersPane.getChildren().clear();
        for (VnCharacter vc : characters) {
            GameCharacter c = vc.character();
            Path file = c.imagePath() == null ? null : ctx.imageCache().resolve(c.imagePath());
            boolean hidden = vc.isSpoiler() && !showSpoilers && !revealedSpoilers.contains(c.id());
            CharacterCard.Options options = new CharacterCard.Options(hidden,
                    ctx.contentFilter().isSensitive(c.imageSexual(), c.imageViolence()),
                    showSensitive, withRoute.contains(c.id()));
            charactersPane.getChildren().add(new CharacterCard(vc, file, options,
                    () -> routesPane.addRouteFor(vc),
                    () -> {
                        revealedSpoilers.add(c.id());
                        renderCharacters();
                    }));
        }
        // El diálogo de rutas solo ofrece personajes spoiler si se están mostrando
        routesPane.setCharacters(characters, showSpoilers);

        long spoilerCount = characters.stream().filter(VnCharacter::isSpoiler).count();
        showSpoilersCheck.setVisible(spoilerCount > 0);
        showSpoilersCheck.setManaged(spoilerCount > 0);
        boolean anySensitive = characters.stream().map(VnCharacter::character)
                .anyMatch(ch -> ctx.contentFilter().isSensitive(ch.imageSexual(), ch.imageViolence()));
        showSensitiveCheck.setVisible(anySensitive);
        showSensitiveCheck.setManaged(anySensitive);
        charactersStatus.setText("");
        charactersStatus.setManaged(false);
        if (characters.isEmpty()) {
            // Estado vacío (vacio-personajes.png): las rutas se pueden crear igualmente
            Button addRoute = new Button("+  Añadir ruta");
            addRoute.setOnAction(event -> routesPane.openCreateDialog());
            VBox empty = EmptyState.create(null, "VNDB no tiene personajes principales para esta novela",
                    addRoute,
                    "Puedes crear las rutas igualmente y escribir el nombre de la heroína a mano. Si "
                            + "alguien los añade en VNDB, aparecerán al pulsar «Actualizar desde VNDB».");
            empty.getStyleClass().add("empty-dashed");
            // Que ocupe todo el ancho de la sección
            empty.prefWidthProperty().bind(charactersPane.widthProperty());
            charactersPane.getChildren().setAll(empty);
        }
    }

    // =========================================================== otras acciones

    @FXML
    private void onBack() {
        navigator.showLibrary();
    }

    @FXML
    private void onRemove() {
        if (ctx.launchService().isRunning(vnId)) {
            Dialogs.error(removeButton, "Cierra el juego antes de quitarlo de la biblioteca.");
            return;
        }
        if (!Dialogs.confirm(removeButton, "¿Quitar \"" + entry.vn().title() + "\" de la biblioteca?",
                "Se borrarán también sus rutas y el tiempo de juego registrado.", "Quitar", true)) {
            return;
        }
        removeButton.setDisable(true);
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.libraryService().remove(vnId);
                    return null; // Callable debe devolver algo; aquí no hay nada útil que devolver
                },
                ignored -> navigator.showLibrary(),
                error -> {
                    removeButton.setDisable(false);
                    Dialogs.error(removeButton, error.getMessage());
                });
    }
}

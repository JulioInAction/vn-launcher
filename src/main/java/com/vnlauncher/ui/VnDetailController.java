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
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
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
    @FXML private StackPane coverBox;
    @FXML private Label titleLabel;
    @FXML private Label altTitleLabel;
    @FXML private Label metaLabel;
    @FXML private Button playButton;
    @FXML private Button exeButton;
    @FXML private Label exePathLabel;
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
        launchFlow = new LaunchFlow(ctx, playButton);
        removeButton.setDisable(true);   // hasta que se cargue la VN
        playButton.setDisable(true);
        BackgroundTasks.run(ctx.background(), () -> ctx.libraryService().find(vnId),
                maybeEntry -> {
                    if (maybeEntry.isEmpty()) {
                        navigator.showLibrary(); // ya no está en la biblioteca
                    } else {
                        renderHeader(maybeEntry.get());
                    }
                },
                error -> titleLabel.setText("Error: " + error.getMessage()));
        reloadPlayData(); // tiempo jugado (suma de sesiones)

        // La etiqueta de estado solo ocupa sitio cuando tiene texto (binding: se recalcula sola)
        launchStatusLabel.managedProperty().bind(launchStatusLabel.textProperty().isNotEmpty());

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
        showSensitiveCheck.selectedProperty().addListener((obs, oldValue, selected) -> renderCharacters());
        charactersStatus.setText("Cargando personajes...");
        BackgroundTasks.run(ctx.background(), () -> ctx.characterService().getPrimaryCharacters(vnId),
                loaded -> {
                    characters = loaded;
                    renderCharacters();
                },
                error -> charactersStatus.setText(
                        "No se pudieron cargar los personajes: " + error.getMessage()));
    }

    // =========================================================== cabecera

    private void renderHeader(LibraryEntry loaded) {
        VisualNovel vn = loaded.vn();
        Path file = vn.imagePath() == null ? null : ctx.imageCache().resolve(vn.imagePath());
        CoverView cover = new CoverView(file,
                ctx.contentFilter().isSensitive(vn.imageSexual(), vn.imageViolence()), 220, 330, vn.title());
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
        coverBox.getChildren().setAll(cover);

        titleLabel.setText(vn.title());
        altTitleLabel.setText(vn.altTitle() == null ? "" : vn.altTitle());
        altTitleLabel.setManaged(vn.altTitle() != null);
        metaLabel.setText(SearchController.meta(vn));
        renderDescription(vn.description());

        renderLaunchInfo(loaded);
        // El listener se añade DESPUÉS de poner el valor inicial, para que no cuente como cambio
        localeEmulatorCheck.selectedProperty().addListener(
                (obs, oldValue, selected) -> onLocaleEmulatorToggled(selected));
        removeButton.setDisable(false);
    }

    /** Parte de la cabecera que cambia al configurar o lanzar el juego. */
    private void renderLaunchInfo(LibraryEntry loaded) {
        entry = loaded;
        exeButton.setText(loaded.hasExecutable() ? "Cambiar ejecutable" : "Elegir ejecutable");
        exePathLabel.setText(loaded.hasExecutable() ? loaded.exePath()
                : "Aún no has elegido el ejecutable del juego.");
        localeEmulatorCheck.setSelected(loaded.useLocaleEmulator());
        lastPlayedLabel.setText(RelativeTime.format(loaded.lastPlayedAt(), Instant.now(), ZoneId.systemDefault()));
        renderPlayButton();
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

    private void onLocaleEmulatorToggled(boolean selected) {
        if (entry == null || selected == entry.useLocaleEmulator()) {
            return; // nada que guardar (p. ej. el cambio lo hizo renderLaunchInfo)
        }
        BackgroundTasks.run(ctx.background(), () -> {
                    ctx.launchService().setUseLocaleEmulator(vnId, selected);
                    // Si se activa y no sabemos dónde está LEProc.exe, preguntarlo ya
                    return selected && ctx.settingsService().localeEmulatorPath().isEmpty();
                },
                needsPath -> {
                    reloadPlayData();
                    if (needsPath) {
                        launchFlow.info("Locale Emulator", "Elige el archivo LEProc.exe de la carpeta donde "
                                + "descomprimiste Locale Emulator. Solo se pide una vez.");
                        launchFlow.chooseLocaleEmulator(
                                () -> launchStatusLabel.setText("Locale Emulator configurado."));
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
        charactersStatus.setText(characters.isEmpty()
                ? "VNDB no tiene personajes principales para esta novela." : "");
        charactersStatus.setManaged(characters.isEmpty());
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
                "Se borrarán también sus rutas y el tiempo de juego registrado.")) {
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

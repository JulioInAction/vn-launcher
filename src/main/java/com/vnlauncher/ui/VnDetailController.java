package com.vnlauncher.ui;

import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.Route;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.service.CharacterService;
import com.vnlauncher.service.ContentFilter;
import com.vnlauncher.service.ImageCacheService;
import com.vnlauncher.service.LibraryService;
import com.vnlauncher.service.RouteService;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;

/**
 * Ficha de una VN de la biblioteca: cabecera con portada y datos, y pestañas con la descripción,
 * los personajes principales y las rutas. En las próximas fases se añadirán botón de jugar (5)
 * y tiempo de juego (6).
 *
 * <p>Las pestañas Personajes y Rutas están conectadas: marcar "+ Tiene ruta" en un personaje crea
 * una ruta, y cada vez que cambian las rutas se redibujan las tarjetas ("✓ Con ruta").
 */
public class VnDetailController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LibraryService library;
    private final CharacterService characterService;
    private final RouteService routeService;
    private final ContentFilter contentFilter;
    private final ImageCacheService images;
    private final ExecutorService background;
    private final Navigator navigator;
    private final String vnId;

    @FXML private StackPane coverBox;
    @FXML private Label titleLabel;
    @FXML private Label altTitleLabel;
    @FXML private Label metaLabel;
    @FXML private Label addedLabel;
    @FXML private Label descriptionLabel;
    @FXML private Button removeButton;

    @FXML private Tab charactersTab;
    @FXML private Label charactersStatus;
    @FXML private CheckBox showSpoilersCheck;
    @FXML private FlowPane charactersPane;

    @FXML private Tab routesTab;
    private RoutesPane routesPane;

    private VisualNovel vn;
    /** Personajes cargados (todos, incluidos los spoiler); null mientras se cargan. */
    private List<VnCharacter> characters;
    /** Rutas actuales de la VN. */
    private List<Route> routes = List.of();

    public VnDetailController(LibraryService library, CharacterService characterService,
                              RouteService routeService, ContentFilter contentFilter, ImageCacheService images,
                              ExecutorService background, Navigator navigator, String vnId) {
        this.library = library;
        this.characterService = characterService;
        this.routeService = routeService;
        this.contentFilter = contentFilter;
        this.images = images;
        this.background = background;
        this.navigator = navigator;
        this.vnId = vnId;
    }

    @FXML
    private void initialize() {
        removeButton.setDisable(true); // hasta que se cargue la VN
        BackgroundTasks.run(background, () -> library.find(vnId),
                this::render,
                error -> titleLabel.setText("Error: " + error.getMessage()));

        // Los personajes se cargan a la vez, en otra tarea: la primera vez tardan (VNDB + imágenes)
        // y no queremos que la cabecera espere por ellos.
        showSpoilersCheck.setVisible(false);
        showSpoilersCheck.selectedProperty().addListener((obs, oldValue, selected) -> renderCharacters());
        charactersStatus.setText("Cargando personajes...");
        BackgroundTasks.run(background, () -> characterService.getPrimaryCharacters(vnId),
                loaded -> {
                    characters = loaded;
                    renderCharacters();
                },
                error -> charactersStatus.setText(
                        "No se pudieron cargar los personajes: " + error.getMessage()));

        // Pestaña de rutas: un componente propio (RoutesPane) que avisa cuando cambian las rutas.
        routesPane = new RoutesPane(routeService, background, images, contentFilter, vnId);
        routesPane.setOnRoutesChanged(changed -> {
            routes = changed;
            routesTab.setText("Rutas (" + routeService.progressOf(changed) + ")");
            renderCharacters(); // actualizar "+ Tiene ruta" / "✓ Con ruta"
        });
        routesTab.setContent(routesPane);
        routesPane.reload();
    }

    /**
     * Dibuja las tarjetas de personaje. Los que son spoiler solo aparecen si la casilla
     * "Mostrar spoilers" está marcada (por defecto no lo está).
     */
    private void renderCharacters() {
        if (characters == null) {
            return;
        }
        long spoilerCount = characters.stream().filter(VnCharacter::isSpoiler).count();
        boolean showSpoilers = showSpoilersCheck.isSelected();
        // Ids de los personajes que ya tienen alguna ruta (un Set para buscar rápido)
        Set<String> withRoute = routes.stream()
                .map(Route::characterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        charactersPane.getChildren().clear();
        for (VnCharacter vc : characters) {
            if (vc.isSpoiler() && !showSpoilers) {
                continue;
            }
            GameCharacter c = vc.character();
            Path file = c.imagePath() == null ? null : images.resolve(c.imagePath());
            charactersPane.getChildren().add(new CharacterCard(vc, file,
                    contentFilter.isSensitive(c.imageSexual(), c.imageViolence()),
                    withRoute.contains(c.id()), () -> routesPane.addRouteFor(vc)));
        }
        routesPane.setCharacters(characters, showSpoilers);

        charactersTab.setText("Personajes (" + (characters.size() - (showSpoilers ? 0 : spoilerCount)) + ")");
        showSpoilersCheck.setVisible(spoilerCount > 0);
        showSpoilersCheck.setText("Mostrar spoilers (" + spoilerCount + ")");
        if (characters.isEmpty()) {
            charactersStatus.setText("VNDB no tiene personajes principales para esta novela.");
        } else {
            charactersStatus.setText("Personajes principales según VNDB: candidatas a heroína."
                    + (spoilerCount > 0 && !showSpoilers
                    ? " Hay " + spoilerCount + " oculto(s) por spoiler." : ""));
        }
    }

    private void render(Optional<LibraryEntry> maybeEntry) {
        if (maybeEntry.isEmpty()) {
            navigator.showLibrary(); // ya no está en la biblioteca
            return;
        }
        LibraryEntry entry = maybeEntry.get();
        vn = entry.vn();

        Path file = vn.imagePath() == null ? null : images.resolve(vn.imagePath());
        CoverView cover = new CoverView(file,
                contentFilter.isSensitive(vn.imageSexual(), vn.imageViolence()), 256, 360);
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
        metaLabel.setText(SearchResultCell.subtitle(vn));
        addedLabel.setText("En tu biblioteca desde el "
                + DATE.format(entry.addedAt().atZone(ZoneId.systemDefault())));
        descriptionLabel.setText(vn.description() == null ? "Sin descripción." : vn.description());
        removeButton.setDisable(false);
    }

    @FXML
    private void onBack() {
        navigator.showLibrary();
    }

    @FXML
    private void onRemove() {
        if (!Dialogs.confirm(removeButton, "¿Quitar \"" + vn.title() + "\" de la biblioteca?",
                "Se borrarán también sus rutas y el tiempo de juego registrado.")) {
            return;
        }
        removeButton.setDisable(true);
        BackgroundTasks.run(background, () -> {
                    library.remove(vnId);
                    return null; // Callable debe devolver algo; aquí no hay nada útil que devolver
                },
                ignored -> navigator.showLibrary(),
                error -> {
                    removeButton.setDisable(false);
                    Dialogs.error(removeButton, error.getMessage());
                });
    }
}

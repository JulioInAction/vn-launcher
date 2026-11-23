package com.vnlauncher.ui;

import com.vnlauncher.model.VnStats;
import com.vnlauncher.util.VnFacts;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué lleva la ficha según se abra desde la biblioteca o como vista previa de un resultado de la
 * búsqueda (vn-preview-*.png). Sin interfaz, para poder probarlo: {@link VnDetailController}
 * solo enseña u oculta lo que aquí se decide.
 *
 * <p>La vista previa es la ficha de una novela que aún no está en la biblioteca: no tiene estado,
 * estanterías, rutas, ejecutable ni partidas. En su lugar, «Añadir a la biblioteca» y, en el sitio de
 * la línea de datos, tarjetas con la duración, la valoración y los idiomas. Al añadirla, la misma
 * ficha pasa a ser la de la biblioteca sin cambiar de pantalla.
 */
final class DetailLayout {

    /** Desde dónde se abre. */
    enum Mode {
        /** Una VN de la biblioteca. */
        LIBRARY,
        /** Un resultado de la búsqueda que aún no está en la biblioteca. */
        PREVIEW
    }

    /** A dónde lleva «‹» de arriba: de dónde se vino. */
    enum Back {
        LIBRARY("‹  Biblioteca"),
        SEARCH("‹  Buscar");

        private final String label;

        Back(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** Entradas posibles del menú «⋯». */
    enum MenuEntry {
        OPEN_CONFIG, OPEN_VNDB, REFRESH, REMOVE
    }

    /**
     * Una tarjeta de la vista previa: etiqueta pequeña arriba y el valor debajo.
     */
    record FactCard(String label, String value) {
    }

    /** Qué partes de la ficha se ven. */
    record Sections(
            boolean statusAndShelves,   // chips de estado y estanterías
            boolean playActions,        // «Jugar», «Opciones del juego» y el estado de «Actualizar»
            boolean addAction,          // «Añadir a la biblioteca» y su nota
            boolean dataLine,           // «37 min jugados · última partida · rutas»
            boolean factCards,          // duración, valoración e idiomas
            boolean exeNotice,          // aviso de ejecutable perdido
            boolean changeCover,        // «Cambiar portada» bajo la portada
            boolean routes,             // sección «Rutas»
            boolean characterRoutes,    // «Marcar con ruta» en los personajes
            boolean tagsClickable       // pulsar una etiqueta filtra la biblioteca
    ) {
    }

    private static final Sections LIBRARY_SECTIONS =
            new Sections(true, true, false, true, false, true, true, true, true, true);
    private static final Sections PREVIEW_SECTIONS =
            new Sections(false, false, true, false, true, false, false, false, false, false);

    private DetailLayout() {
    }

    static Sections sections(Mode mode) {
        return mode == Mode.PREVIEW ? PREVIEW_SECTIONS : LIBRARY_SECTIONS;
    }

    /**
     * El menú «⋯», de arriba abajo. La vista previa solo puede abrir la VN en VNDB: no hay nada que
     * actualizar ni quitar, ni programa de configuración.
     */
    static List<MenuEntry> menu(Mode mode, boolean hasConfigProgram) {
        if (mode == Mode.PREVIEW) {
            return List.of(MenuEntry.OPEN_VNDB);
        }
        List<MenuEntry> entries = new ArrayList<>();
        if (hasConfigProgram) {
            entries.add(MenuEntry.OPEN_CONFIG);
        }
        entries.add(MenuEntry.OPEN_VNDB);
        entries.add(MenuEntry.REFRESH);
        entries.add(MenuEntry.REMOVE);
        return entries;
    }

    /**
     * Las tarjetas de la vista previa, en este orden. Si falta un dato, su tarjeta no sale (no se
     * pone «Sin datos»); sin ninguno, la lista queda vacía y no hay fila.
     */
    static List<FactCard> factCards(VnStats stats) {
        List<FactCard> cards = new ArrayList<>();
        if (VnFacts.hasLength(stats.lengthMinutes(), stats.lengthVotes())) {
            cards.add(new FactCard("Duración", VnFacts.length(stats.lengthMinutes(), stats.lengthVotes())));
        }
        if (VnFacts.hasRating(stats.rating(), stats.voteCount())) {
            cards.add(new FactCard("Valoración", VnFacts.rating(stats.rating(), stats.voteCount())));
        }
        if (!stats.languages().isEmpty()) {
            cards.add(new FactCard("Idiomas", VnFacts.languages(stats.languages(), stats.originalLanguage())));
        }
        return cards;
    }
}

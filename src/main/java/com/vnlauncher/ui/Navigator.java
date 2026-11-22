package com.vnlauncher.ui;

import com.vnlauncher.model.PlayStatus;
import com.vnlauncher.model.SearchHit;
import com.vnlauncher.model.Shelf;
import com.vnlauncher.model.VnTag;

/**
 * Cambiar de pantalla. Lo implementa MainController (que es quien tiene la zona central)
 * y se pasa a los demás controladores. Así, por ejemplo, LibraryController puede abrir el
 * detalle de una VN sin conocer a MainController ni cómo está montada la ventana.
 */
public interface Navigator {

    /** Toda la biblioteca, sin filtros. */
    void showLibrary();

    /** Biblioteca mostrando solo las VNs en un estado (Jugando, En pausa...); se combina con la etiqueta. */
    void showLibrary(PlayStatus status);

    /** Biblioteca mostrando solo las VNs de una estantería; se combina con la etiqueta. */
    void showLibraryWithShelf(Shelf shelf);

    /** Biblioteca mostrando solo las VNs con esta etiqueta (al pulsarla en la ficha). */
    void showLibraryWithTag(VnTag tag);

    /** Vuelve a la biblioteca tal como estaba (con su estado y su etiqueta), p. ej. desde la ficha. */
    void backToLibrary();

    void showSearch();

    void showSettings();

    void showVnDetail(String vnId);

    /**
     * La ficha de una VN de la biblioteca abierta desde «Buscar» («Abrir ficha»). La búsqueda no se
     * descarta: «‹ Buscar» vuelve a ella tal como estaba.
     */
    void showVnDetailFromSearch(String vnId);

    /**
     * La vista previa de un resultado que aún no está en la biblioteca («Ver ficha»). Igual que
     * {@link #showVnDetailFromSearch}, la búsqueda se queda como estaba.
     */
    void showVnPreview(SearchHit hit);

    /** Vuelve a «Buscar» con los resultados, la selección y el scroll que tenía. */
    void backToSearch();

    /**
     * Avisa de que han cambiado datos que muestra la barra lateral (los contadores de cada estado):
     * al añadir o quitar una VN, cambiar una ruta o su estado, o lanzar un juego.
     */
    void refreshSidebar();

    /**
     * Se ha creado, cambiado, reordenado o borrado una estantería, o se ha metido o sacado una VN:
     * se actualizan la barra lateral y la pantalla visible.
     */
    void shelvesChanged();
}

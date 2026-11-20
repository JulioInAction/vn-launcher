package com.vnlauncher.ui;

import com.vnlauncher.model.VisualNovel;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

/**
 * Cómo se dibuja cada resultado en la lista: título en grande y debajo "desarrollador · fecha".
 *
 * <p>Ojo: JavaFX REUTILIZA las celdas al hacer scroll (solo crea las que caben en pantalla).
 * Por eso creamos los Label una vez en el constructor y en updateItem solo cambiamos su texto,
 * y por eso hay que limpiar la celda cuando queda vacía.
 */
class SearchResultCell extends ListCell<VisualNovel> {

    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final VBox box = new VBox(2, title, subtitle);

    SearchResultCell() {
        // Truco habitual: por defecto la celda pide el ancho de su texto más largo, y con un título
        // largo la ListView saca una barra de scroll horizontal. Con prefWidth 0 la celda se ajusta
        // al ancho de la lista y los Label recortan el texto sobrante con "...".
        setPrefWidth(0);
        title.getStyleClass().add("result-title");
        subtitle.getStyleClass().add("result-subtitle");
    }

    @Override
    protected void updateItem(VisualNovel vn, boolean empty) {
        super.updateItem(vn, empty); // obligatorio: JavaFX actualiza su estado interno aquí
        if (empty || vn == null) {
            setGraphic(null);
            return;
        }
        title.setText(vn.title());
        subtitle.setText(subtitle(vn));
        setGraphic(box);
    }

    /** "Key · 2004-04-28" (o "Desarrollador desconocido · Sin fecha" si faltan datos). */
    static String subtitle(VisualNovel vn) {
        String devs = vn.developerNames().isEmpty() ? "Desarrollador desconocido" : vn.developerNames();
        String date = vn.released() == null ? "Sin fecha" : vn.released();
        return devs + "  ·  " + date;
    }
}

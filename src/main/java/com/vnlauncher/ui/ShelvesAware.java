package com.vnlauncher.ui;

/**
 * Pantallas que enseñan estanterías (la biblioteca filtrada por una, los chips de la ficha). Si se
 * crea, cambia o borra una desde la barra lateral, MainController avisa a la pantalla visible.
 */
interface ShelvesAware {

    void shelvesChanged();
}

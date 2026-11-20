package com.vnlauncher.ui;

/**
 * Cambiar de pantalla. Lo implementa MainController (que es quien tiene la zona central)
 * y se pasa a los demás controladores. Así, por ejemplo, LibraryController puede abrir el
 * detalle de una VN sin conocer a MainController ni cómo está montada la ventana.
 */
public interface Navigator {

    void showLibrary();

    void showSearch();

    void showVnDetail(String vnId);
}

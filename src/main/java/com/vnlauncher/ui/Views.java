package com.vnlauncher.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/** Utilidad para cargar vistas FXML con un controlador que hemos creado nosotros. */
public final class Views {

    private Views() {
    }

    /**
     * Carga un FXML de este paquete usando el controlador indicado.
     *
     * <p>Por defecto FXMLLoader crea el controlador llamando a su constructor vacío. Nuestros
     * controladores reciben sus dependencias (servicios) por constructor, así que lo creamos
     * nosotros y, con la "controller factory", le decimos a FXMLLoader que use ese objeto.
     */
    public static Parent load(String fxml, Object controller) {
        URL url = Views.class.getResource(fxml);
        if (url == null) {
            throw new IllegalArgumentException("No existe la vista " + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(type -> controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Error cargando " + fxml, e);
        }
    }
}

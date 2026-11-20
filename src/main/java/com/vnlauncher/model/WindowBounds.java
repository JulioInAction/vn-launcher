package com.vnlauncher.model;

/**
 * Posición y tamaño de la ventana principal (en píxeles de pantalla), para abrirla la próxima vez
 * como el usuario la dejó.
 *
 * @param maximized si estaba maximizada; x, y, width y height son entonces los de antes de maximizar
 */
public record WindowBounds(double x, double y, double width, double height, boolean maximized) {

    /** Fracción de la pantalla que ocupa la ventana la primera vez. */
    static final double FIRST_TIME_FRACTION = 0.9;
    /** Tope la primera vez, para que en monitores enormes no salga desproporcionada. */
    static final double FIRST_TIME_MAX_WIDTH = 1680;
    static final double FIRST_TIME_MAX_HEIGHT = 1050;

    /**
     * Tamaño de la primera vez: el 90 % de la zona útil de la pantalla (sin la barra de tareas),
     * hasta 1680 × 1050, centrada. Todo redondeado a píxeles enteros: con decimales, el contenido
     * podría quedar a medio píxel y verse borroso.
     *
     * <p>screenX, screenY, screenWidth y screenHeight son la zona útil de la pantalla principal;
     * minWidth y minHeight, el tamaño mínimo de la ventana.
     */
    public static WindowBounds firstTime(double screenX, double screenY, double screenWidth, double screenHeight,
                                         double minWidth, double minHeight) {
        double width = clamp(Math.min(screenWidth * FIRST_TIME_FRACTION, FIRST_TIME_MAX_WIDTH), minWidth, screenWidth);
        double height = clamp(Math.min(screenHeight * FIRST_TIME_FRACTION, FIRST_TIME_MAX_HEIGHT), minHeight, screenHeight);
        width = Math.round(width);
        height = Math.round(height);
        double x = Math.round(screenX + (screenWidth - width) / 2);
        double y = Math.round(screenY + (screenHeight - height) / 2);
        return new WindowBounds(x, y, width, height, false);
    }

    /** value, pero sin bajar de min ni pasar de max (si min > max, gana max: la pantalla manda). */
    private static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    public WindowBounds withMaximized(boolean value) {
        return new WindowBounds(x, y, width, height, value);
    }
}

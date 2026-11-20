package com.vnlauncher.model;

/**
 * Tema de la interfaz elegido por el usuario. {@link #settingValue()} es lo que se guarda en la
 * tabla setting (clave "theme"). SYSTEM ("Según el sistema") no es un tema en sí: la interfaz lo
 * traduce en DARK o LIGHT según el modo de color del sistema operativo.
 */
public enum Theme {
    DARK("oscuro", "Oscuro"),
    LIGHT("claro", "Claro"),
    SYSTEM("sistema", "Según el sistema");

    private final String settingValue;
    private final String label;

    Theme(String settingValue, String label) {
        this.settingValue = settingValue;
        this.label = label;
    }

    public String settingValue() {
        return settingValue;
    }

    /** Texto para la pantalla de Ajustes. */
    public String label() {
        return label;
    }

    /** Valor guardado → tema. Si no hay valor o no se reconoce, el oscuro (el de por defecto). */
    public static Theme fromSetting(String value) {
        for (Theme theme : values()) {
            if (theme.settingValue.equals(value)) {
                return theme;
            }
        }
        return DARK;
    }
}

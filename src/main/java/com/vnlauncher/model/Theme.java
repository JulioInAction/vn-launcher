package com.vnlauncher.model;

/**
 * Tema de la interfaz. {@link #settingValue()} es lo que se guarda en la tabla setting
 * (clave "theme"); la interfaz traduce LIGHT en la clase CSS "tema-claro" (ver styles.css).
 */
public enum Theme {
    DARK("oscuro"),
    LIGHT("claro");

    private final String settingValue;

    Theme(String settingValue) {
        this.settingValue = settingValue;
    }

    public String settingValue() {
        return settingValue;
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

    public Theme toggled() {
        return this == DARK ? LIGHT : DARK;
    }
}

package com.vnlauncher.model;

/**
 * Papel de un personaje en una VN. Un enum en vez de un String: el compilador comprueba
 * que no escribimos "primay", y en un switch nos avisa si falta algún caso.
 *
 * <p>Están ordenados de más a menos importante; {@link #ordinal()} sirve para compararlos.
 */
public enum CharacterRole {
    MAIN,     // protagonista
    PRIMARY,  // personaje principal (aquí suelen estar las heroínas)
    SIDE,     // secundario
    APPEARS;  // solo aparece

    /** Valor tal como lo usa VNDB y nuestra BD: "main", "primary"... */
    public String apiValue() {
        return name().toLowerCase();
    }

    public static CharacterRole fromApi(String value) {
        return valueOf(value.toUpperCase()); // lanza IllegalArgumentException si no existe
    }

    /** true si este rol es más importante que el otro (MAIN gana a PRIMARY, etc.). */
    public boolean moreImportantThan(CharacterRole other) {
        return ordinal() < other.ordinal();
    }
}

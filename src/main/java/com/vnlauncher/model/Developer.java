package com.vnlauncher.model;

/**
 * Desarrollador de una VN.
 *
 * @param original nombre en su idioma original (p. ej. japonés); puede ser null.
 */
public record Developer(String id, String name, String original) {
}

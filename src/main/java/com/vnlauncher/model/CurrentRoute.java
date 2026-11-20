package com.vnlauncher.model;

/**
 * La ruta que se está jugando (la primera "En curso" en el orden recomendado), para la franja
 * "Continuar" de la biblioteca.
 *
 * @param heroineName nombre de la heroína; null si es una ruta sin heroína
 */
public record CurrentRoute(String routeName, String heroineName) {
}

package com.vnlauncher.ui;

/**
 * Lo implementan las pantallas que muestran datos de juego (botón "Jugando...", horas,
 * franja "Continuar"). MainController les avisa cuando un juego se abre o se cierra.
 * Siempre se llama en el hilo de la interfaz.
 */
interface PlaytimeAware {

    void playtimeChanged(String vnId);
}

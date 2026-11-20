package com.vnlauncher.service;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Un juego recién lanzado. En la Fase 6 el registro de tiempo de juego partirá de aquí.
 *
 * @param process   el proceso que arrancamos nosotros. OJO: puede no ser el juego en sí
 *                  (LEProc.exe, un lanzador, o cmd.exe si hizo falta pedir permisos de administrador),
 *                  y puede cerrarse enseguida aunque el juego siga abierto.
 * @param gameDir   carpeta del ejecutable del juego
 * @param elevated  true si se lanzó pidiendo permisos de administrador (ver LaunchService)
 */
public record LaunchedGame(String vnId, Process process, Path gameDir, Instant startedAt, boolean elevated) {
}

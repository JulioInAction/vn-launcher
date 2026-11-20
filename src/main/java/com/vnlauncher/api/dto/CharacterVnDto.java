package com.vnlauncher.api.dto;

/**
 * Aparición de un personaje en una VN.
 *
 * @param role    "main" (protagonista), "primary" (principal), "side" (secundario) o "appears" (cameo)
 * @param spoiler 0 = no es spoiler, 1 = spoiler menor, 2 = spoiler mayor (saber que existe ya destripa algo)
 */
public record CharacterVnDto(String id, String role, int spoiler) {
}

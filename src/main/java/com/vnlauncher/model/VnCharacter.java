package com.vnlauncher.model;

/**
 * Un personaje DENTRO de una VN concreta: su rol y su nivel de spoiler dependen de la VN
 * (corresponde a una fila de la tabla vn_character).
 *
 * @param spoilerLevel 0 = no es spoiler, 1 = menor, 2 = mayor
 */
public record VnCharacter(GameCharacter character, CharacterRole role, int spoilerLevel) {

    /** Se oculta por defecto si tiene cualquier nivel de spoiler. */
    public boolean isSpoiler() {
        return spoilerLevel > 0;
    }
}

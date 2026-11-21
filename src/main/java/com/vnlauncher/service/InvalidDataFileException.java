package com.vnlauncher.service;

/**
 * El archivo elegido (una copia de seguridad o un perfil) no se puede usar: está dañado, es de una
 * versión más nueva de la app, no es de VN Launcher... No es un fallo del programa: el mensaje
 * está pensado para enseñárselo tal cual al usuario.
 */
public class InvalidDataFileException extends Exception {

    public InvalidDataFileException(String userMessage) {
        super(userMessage);
    }
}

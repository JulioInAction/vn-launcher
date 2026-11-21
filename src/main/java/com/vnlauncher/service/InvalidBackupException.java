package com.vnlauncher.service;

/**
 * La copia elegida no se puede restaurar (está dañada o es de una versión más nueva de la app). No
 * es un fallo del programa: el mensaje está pensado para enseñárselo tal cual al usuario.
 */
public class InvalidBackupException extends Exception {

    public InvalidBackupException(String userMessage) {
        super(userMessage);
    }
}

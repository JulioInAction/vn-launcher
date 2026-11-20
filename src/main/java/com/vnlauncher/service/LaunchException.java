package com.vnlauncher.service;

/**
 * No se pudo lanzar el juego. El {@link Reason} permite a la interfaz reaccionar a cada caso
 * (p. ej. abrir el selector de archivos si falta el ejecutable) en vez de solo mostrar el texto.
 */
public class LaunchException extends Exception {

    public enum Reason {
        /** La VN no tiene ejecutable configurado. */
        NO_EXECUTABLE,
        /** El ejecutable configurado ya no existe (juego movido o desinstalado). */
        EXECUTABLE_NOT_FOUND,
        /** Hay que usar Locale Emulator pero no se ha indicado dónde está LEProc.exe. */
        LOCALE_EMULATOR_NOT_CONFIGURED,
        /** La ruta guardada de LEProc.exe ya no existe. */
        LOCALE_EMULATOR_NOT_FOUND,
        /** El sistema operativo no pudo arrancar el proceso. */
        START_FAILED
    }

    private final Reason reason;

    public LaunchException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LaunchException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

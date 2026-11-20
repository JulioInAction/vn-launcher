package com.vnlauncher.util;

import java.time.Duration;

/**
 * Abstracción de "esperar X tiempo".
 *
 * <p>¿Por qué no llamar directamente a Thread.sleep? Porque el código que espera (el límite de
 * peticiones, los reintentos) hay que testearlo, y nadie quiere un test que tarde 5 minutos.
 * En producción se usa {@link #real()}; en los tests pasamos un Sleeper falso que solo apunta
 * cuánto se le pidió esperar (y adelanta un reloj falso).
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    static Sleeper real() {
        return Thread::sleep; // Thread.sleep(Duration) existe desde Java 19
    }
}

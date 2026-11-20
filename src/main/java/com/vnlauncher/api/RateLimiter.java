package com.vnlauncher.api;

import com.vnlauncher.util.Sleeper;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limitador de peticiones con "ventana deslizante": como mucho {@code maxRequests}
 * peticiones en cualquier intervalo de duración {@code window}.
 *
 * <p>Funcionamiento: guardamos el instante de cada petición en una cola (la más antigua delante).
 * Antes de cada petición tiramos los instantes que ya salieron de la ventana; si quedan menos
 * de {@code maxRequests}, adelante. Si no, esperamos justo hasta que la más antigua caduque.
 *
 * <p>Se diferencia de "contar 200 y resetear cada 5 minutos" (ventana fija) en que con
 * ventana fija podrías hacer 200 peticiones al final de un bloque y otras 200 al principio
 * del siguiente: 400 en pocos segundos, y VNDB te cortaría.
 */
public class RateLimiter {

    private static final System.Logger LOG = System.getLogger(RateLimiter.class.getName());

    private final int maxRequests;
    private final Duration window;
    private final InstantSource clock;   // "dame la hora actual"; en tests, un reloj falso
    private final Sleeper sleeper;
    private final Deque<Instant> recent = new ConcurrentLinkedDeque<>();

    public RateLimiter(int maxRequests, Duration window, InstantSource clock, Sleeper sleeper) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests debe ser > 0");
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Peticiones hechas dentro de la ventana actual (p. ej. en los últimos 5 minutos).
     *
     * <p>NO es synchronized a propósito: acquire() puede pasar minutos esperando con el candado
     * cogido, y este método se llama desde la interfaz para el contador del menú lateral. Si
     * esperara al candado, la ventana se congelaría. Por eso la cola es una ConcurrentLinkedDeque,
     * que se puede recorrer con seguridad mientras otro hilo la modifica.
     */
    public int recentCount() {
        Instant windowStart = clock.instant().minus(window);
        return (int) recent.stream().filter(t -> t.isAfter(windowStart)).count();
    }

    /**
     * Cuánto habría que esperar AHORA para hacer una petición: cero si hay hueco; si la ventana
     * está llena, hasta que caduque la más antigua. Tampoco bloquea (como recentCount): la
     * interfaz lo usa para mostrar la cuenta atrás del aviso de límite.
     */
    public Duration timeUntilAvailable() {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        List<Instant> inWindow = recent.stream().filter(t -> t.isAfter(windowStart)).sorted().toList();
        if (inWindow.size() < maxRequests) {
            return Duration.ZERO;
        }
        // Hay que esperar a que salgan las sobrantes: la que ocupa el puesto (n - max) caduca la última
        Instant freesSlot = inWindow.get(inWindow.size() - maxRequests).plus(window);
        return Duration.between(now, freesSlot);
    }

    /**
     * Bloquea el hilo actual hasta que se pueda hacer una petición, y la registra.
     *
     * <p>Es {@code synchronized}: si varios hilos piden permiso a la vez, entran de uno en uno.
     * Mientras uno espera dentro, los demás esperan a la puerta; está bien así, porque
     * de todas formas tampoco podrían hacer su petición antes.
     */
    public synchronized void acquire() throws InterruptedException {
        while (true) {
            Instant now = clock.instant();
            Instant windowStart = now.minus(window);

            // Quitar las peticiones que ya no cuentan (anteriores o iguales al inicio de la ventana)
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.pollFirst();
            }

            if (recent.size() < maxRequests) {
                recent.addLast(now);
                return;
            }

            // Ventana llena: esperar hasta que caduque la petición más antigua y volver a comprobar.
            Duration wait = Duration.between(now, recent.peekFirst().plus(window));
            LOG.log(System.Logger.Level.INFO,
                    "Límite de VNDB alcanzado; esperando {0} s", wait.toSeconds() + 1);
            sleeper.sleep(wait);
        }
    }
}

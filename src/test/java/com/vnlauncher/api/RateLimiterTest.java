package com.vnlauncher.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests con un reloj falso: "dormir" solo adelanta el reloj, así el test que simula
 * 5 minutos de espera tarda milisegundos.
 */
class RateLimiterTest {

    private Instant now;
    private final List<Duration> sleeps = new ArrayList<>();
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-01-01T00:00:00Z");
        // InstantSource tiene un único método abstracto (instant()), así que vale una lambda.
        limiter = new RateLimiter(3, Duration.ofMinutes(5), () -> now, duration -> {
            sleeps.add(duration);
            now = now.plus(duration);
        });
    }

    @Test
    void allowsUpToMaxRequestsWithoutWaiting() throws InterruptedException {
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void waitsUntilOldestRequestLeavesTheWindow() throws InterruptedException {
        limiter.acquire();                    // t = 0
        now = now.plusSeconds(60);
        limiter.acquire();                    // t = 60 s
        limiter.acquire();                    // t = 60 s

        now = now.plusSeconds(30);            // t = 90 s, ventana llena
        limiter.acquire();

        // La primera petición (t=0) caduca en t=300 s; estamos en t=90 -> esperar 210 s.
        assertEquals(List.of(Duration.ofSeconds(210)), sleeps);
    }

    @Test
    void slotsAreFreedAsTimePasses() throws InterruptedException {
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();

        now = now.plus(Duration.ofMinutes(5)).plusSeconds(1);
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();

        assertTrue(sleeps.isEmpty(), "Pasados 5 min, las 3 anteriores ya no cuentan");
    }

    @Test
    void rejectsInvalidLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimiter(0, Duration.ofMinutes(5), () -> now, d -> { }));
    }
}

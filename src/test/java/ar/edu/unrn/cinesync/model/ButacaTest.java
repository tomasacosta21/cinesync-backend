package ar.edu.unrn.cinesync.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Butaca — comportamiento y concurrencia")
class ButacaTest {

    private Butaca butaca;

    @BeforeEach
    void setUp() {
        butaca = new Butaca("A1", 0, 0);
    }

    // ── Estado inicial ────────────────────────────────────────

    @Test
    @DisplayName("Una butaca nueva debe estar LIBRE")
    void nuevaButacaEstaLibre() {
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.LIBRE);
    }

    // ── Transiciones válidas ──────────────────────────────────

    @Test
    @DisplayName("reservar() en butaca LIBRE → RESERVADA, retorna true")
    void reservarButacaLibre() {
        assertThat(butaca.reservar()).isTrue();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.RESERVADA);
    }

    @Test
    @DisplayName("confirmar() en butaca RESERVADA → OCUPADA, retorna true")
    void confirmarButacaReservada() {
        butaca.reservar();
        assertThat(butaca.confirmar()).isTrue();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.OCUPADA);
    }

    @Test
    @DisplayName("liberar() en butaca RESERVADA → LIBRE, retorna true")
    void liberarButacaReservada() {
        butaca.reservar();
        assertThat(butaca.liberar()).isTrue();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.LIBRE);
    }

    // ── Transiciones inválidas ────────────────────────────────

    @Test
    @DisplayName("reservar() en butaca RESERVADA retorna false (sin cambio)")
    void reservarButacaYaReservada() {
        butaca.reservar();
        assertThat(butaca.reservar()).isFalse();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.RESERVADA);
    }

    @Test
    @DisplayName("reservar() en butaca OCUPADA retorna false")
    void reservarButacaOcupada() {
        butaca.reservar();
        butaca.confirmar();
        assertThat(butaca.reservar()).isFalse();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.OCUPADA);
    }

    @Test
    @DisplayName("confirmar() en butaca LIBRE retorna false")
    void confirmarButacaLibre() {
        assertThat(butaca.confirmar()).isFalse();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.LIBRE);
    }

    @Test
    @DisplayName("liberar() en butaca OCUPADA retorna false")
    void liberarButacaOcupada() {
        butaca.reservar();
        butaca.confirmar();
        assertThat(butaca.liberar()).isFalse();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.OCUPADA);
    }

    @Test
    @DisplayName("liberar() en butaca LIBRE retorna false")
    void liberarButacaLibre() {
        assertThat(butaca.liberar()).isFalse();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.LIBRE);
    }

    // ── Flujo completo ────────────────────────────────────────

    @Test
    @DisplayName("Flujo completo: LIBRE → RESERVADA → LIBRE → RESERVADA (otra reserva)")
    void butacaLiberadaPuedeReservarse() {
        butaca.reservar();
        butaca.liberar();
        assertThat(butaca.reservar()).isTrue();
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.RESERVADA);
    }

    // ── Concurrencia ──────────────────────────────────────────

    @RepeatedTest(50)
    @DisplayName("Race condition: solo 1 hilo de 20 debe ganar la reserva")
    void soloUnHiloPuedeReservar() throws InterruptedException {
        int numHilos = 20;
        CountDownLatch largada  = new CountDownLatch(1);
        AtomicInteger  exitosos = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    largada.await();           // todos esperan la señal de largada
                    if (butaca.reservar()) {
                        exitosos.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        largada.countDown();                   // ¡todos largan al mismo tiempo!
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(exitosos.get())
                .as("Exactamente 1 hilo debe ganar la reserva bajo concurrencia")
                .isEqualTo(1);
        assertThat(butaca.getEstado()).isEqualTo(EstadoButaca.RESERVADA);
    }
}

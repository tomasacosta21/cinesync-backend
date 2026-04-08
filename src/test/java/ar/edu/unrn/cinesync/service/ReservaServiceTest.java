package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.sse.SseEmitterService;
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

@DisplayName("ReservaService — lógica de negocio y concurrencia")
class ReservaServiceTest {

    private ReservaServiceImpl reservaService;

    @BeforeEach
    void setUp() {
        SalaServiceImpl salaService = new SalaServiceImpl();
        // SseEmitterService real (sin clientes conectados en test — broadcast no falla)
        SseEmitterService sseService = new SseEmitterService();
        reservaService = new ReservaServiceImpl(salaService, sseService);
    }

    @Test
    @DisplayName("reservar() una butaca libre debe retornar true")
    void reservarButacaLibre() {
        assertThat(reservaService.reservar(1, "A1", "user-1")).isTrue();
    }

    @Test
    @DisplayName("reservar() la misma butaca dos veces: la segunda falla")
    void reservarMismaButacaDosVeces() {
        reservaService.reservar(1, "A1", "user-1");
        assertThat(reservaService.reservar(1, "A1", "user-2")).isFalse();
    }

    @Test
    @DisplayName("confirmar() luego de reservar debe retornar true")
    void confirmarReserva() {
        reservaService.reservar(1, "A1", "user-1");
        assertThat(reservaService.confirmar(1, "A1")).isTrue();
    }

    @Test
    @DisplayName("confirmar() sin reserva previa debe retornar false")
    void confirmarSinReserva() {
        assertThat(reservaService.confirmar(1, "A1")).isFalse();
    }

    @Test
    @DisplayName("liberar() una reserva permite que otro usuario reserve")
    void liberarPermiteNuevaReserva() {
        reservaService.reservar(1, "A1", "user-1");
        reservaService.liberar(1, "A1");
        assertThat(reservaService.reservar(1, "A1", "user-2")).isTrue();
    }

    @Test
    @DisplayName("reservar() en sala inexistente lanza IllegalArgumentException")
    void reservarSalaInexistente() {
        assertThatThrownBy(() -> reservaService.reservar(99, "A1", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reservar() butaca inexistente lanza IllegalArgumentException")
    void reservarButacaInexistente() {
        assertThatThrownBy(() -> reservaService.reservar(1, "Z99", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @RepeatedTest(20)
    @DisplayName("50 hilos compiten por la misma butaca: exactamente 1 gana")
    void concurrencia50HilosUnSoloGanador() throws InterruptedException {
        int concurrencia = 50;
        CountDownLatch largada  = new CountDownLatch(1);
        AtomicInteger  exitosos = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(concurrencia);

        for (int i = 0; i < concurrencia; i++) {
            final String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    largada.await();
                    if (reservaService.reservar(1, "E6", userId)) {
                        exitosos.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        largada.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(exitosos.get())
                .as("Solo 1 de %d hilos debe lograr reservar E6".formatted(concurrencia))
                .isEqualTo(1);
    }

    @RepeatedTest(10)
    @DisplayName("Reservas simultáneas en distintas butacas son independientes")
    void reservasEnDistintasButacasSonIndependientes() throws InterruptedException {
        int numButacas = 10;
        CountDownLatch largada   = new CountDownLatch(1);
        CountDownLatch terminado = new CountDownLatch(numButacas);
        AtomicInteger  exitosos  = new AtomicInteger(0);
        ExecutorService executor  = Executors.newFixedThreadPool(numButacas);

        for (int i = 0; i < numButacas; i++) {
            final String butacaId = "A" + (i + 1);
            final String userId   = "user-" + i;
            executor.submit(() -> {
                try {
                    largada.await();
                    if (reservaService.reservar(1, butacaId, userId)) {
                        exitosos.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    terminado.countDown();
                }
            });
        }

        largada.countDown();
        terminado.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(exitosos.get())
                .as("Cada hilo reserva una butaca distinta → todos deben ganar")
                .isEqualTo(numButacas);
    }
}

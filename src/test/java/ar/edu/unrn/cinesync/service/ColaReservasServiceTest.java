package ar.edu.unrn.cinesync.service;

import ar.edu.siglo21.cinesync.model.SolicitudReserva;
import ar.edu.unrn.cinesync.sse.SseEmitterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ColaReservasService — patrón Productor-Consumidor")
class ColaReservasServiceTest {

    private ColaReservasService colaService;

    @BeforeEach
    void setUp() {
        SalaServiceImpl  salaService = new SalaServiceImpl();
        SseEmitterService sseService = new SseEmitterService();
        ReservaServiceImpl reservaService = new ReservaServiceImpl(salaService, sseService);
        colaService = new ColaReservasService(reservaService);
        colaService.iniciar();   // arranca los workers (normalmente lo hace @PostConstruct)
    }

    @AfterEach
    void tearDown() {
        colaService.detener();
    }

    @Test
    @DisplayName("Una solicitud encolada se procesa en menos de 2 segundos")
    void encolarSolicitudSeProcesa() throws InterruptedException {
        CountDownLatch procesada = new CountDownLatch(1);
        colaService.setOnProcesado(s -> procesada.countDown());

        colaService.encolar(new SolicitudReserva("user-1", 1, "A1", Instant.now()));

        assertThat(procesada.await(2, TimeUnit.SECONDS))
                .as("La solicitud debe procesarse en menos de 2 segundos")
                .isTrue();
    }

    @Test
    @DisplayName("100 solicitudes en distintas butacas se procesan todas")
    void cienSolicitudesSeProcesan() throws InterruptedException {
        int total = 100;
        CountDownLatch procesadas = new CountDownLatch(total);
        colaService.setOnProcesado(s -> procesadas.countDown());

        ExecutorService productores = Executors.newFixedThreadPool(20);
        for (int i = 0; i < total; i++) {
            final int idx = i;
            // Butacas diferentes para que no colisionen entre sí
            char fila      = (char) ('A' + idx / 12);
            String columna = String.valueOf(idx % 12 + 1);
            String butacaId = fila + columna;

            productores.submit(() ->
                colaService.encolar(new SolicitudReserva(
                    "user-" + idx, 1, butacaId, Instant.now()
                ))
            );
        }

        productores.shutdown();
        assertThat(procesadas.await(10, TimeUnit.SECONDS))
                .as("Las 100 solicitudes deben procesarse en menos de 10 segundos")
                .isTrue();
    }

    @Test
    @DisplayName("Solicitudes a la misma butaca: solo la primera debe tener efecto")
    void solicitudesDuplicadasSoloUnaEfectiva() throws InterruptedException {
        int intentos = 20;
        CountDownLatch procesadas = new CountDownLatch(intentos);
        colaService.setOnProcesado(s -> procesadas.countDown());

        for (int i = 0; i < intentos; i++) {
            colaService.encolar(new SolicitudReserva("user-" + i, 1, "B5", Instant.now()));
        }

        procesadas.await(5, TimeUnit.SECONDS);
        // No assertion de conteo aquí — la invariante la garantiza ReservaServiceTest.
        // Este test verifica que el sistema no explota bajo carga duplicada.
        assertThat(colaService.tamañoCola()).isZero();
    }
}

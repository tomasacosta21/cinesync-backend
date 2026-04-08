package ar.edu.unrn.cinesync.service;

import ar.edu.siglo21.cinesync.model.SolicitudReserva;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Implementa el patrón Productor-Consumidor del material de la materia
 * (sección 5.3 del PDF — Jorba/Suppi).
 *
 * PRODUCTOR: los usuarios del frontend llaman a encolar() — generan SolicitudReserva
 *            y las depositan en la LinkedBlockingQueue sin bloquearse.
 *
 * CONSUMIDORES: NUM_WORKERS hilos del ExecutorService (granja de workers) toman
 *               solicitudes de la cola y las procesan llamando a ReservaService.
 *               Si la cola está vacía, los workers se bloquean esperando (poll con timeout),
 *               liberando CPU sin busy-waiting.
 *
 * LinkedBlockingQueue garantiza:
 *   - Operaciones put/take thread-safe sin synchronized explícito
 *   - Bloqueo natural cuando la cola está llena (productor) o vacía (consumidor)
 *   - FIFO: las reservas se procesan en el orden en que llegaron
 */
@Service
public class ColaReservasService {

    private static final int CAPACIDAD_COLA = 500;
    private static final int NUM_WORKERS    = 4;

    private final BlockingQueue<SolicitudReserva> cola =
            new LinkedBlockingQueue<>(CAPACIDAD_COLA);

    private final ExecutorService workers =
            Executors.newFixedThreadPool(NUM_WORKERS);

    private final ReservaService reservaService;

    /** Callback opcional — usado en tests para saber cuándo se procesó una solicitud. */
    private volatile Consumer<SolicitudReserva> onProcesado = s -> {};

    private volatile boolean activo = true;

    public ColaReservasService(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    /** Arranca los worker threads al iniciar el contexto Spring. */
    @PostConstruct
    public void iniciar() {
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers.submit(this::loopConsumidor);
        }
    }

    /**
     * Encola una solicitud de reserva (lado PRODUCTOR).
     * Si la cola está llena, bloquea al productor hasta que haya lugar.
     */
    public void encolar(SolicitudReserva solicitud) {
        try {
            cola.put(solicitud);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Loop del CONSUMIDOR: toma solicitudes y las procesa.
     * poll() con timeout evita busy-waiting — el hilo cede CPU mientras espera.
     */
    private void loopConsumidor() {
        while (activo || !cola.isEmpty()) {
            try {
                SolicitudReserva solicitud = cola.poll(500, TimeUnit.MILLISECONDS);
                if (solicitud != null) {
                    reservaService.reservar(
                            solicitud.salaId(),
                            solicitud.butacaId(),
                            solicitud.usuarioId()
                    );
                    onProcesado.accept(solicitud);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Detiene los workers al apagar Spring. */
    @PreDestroy
    public void detener() {
        activo = false;
        workers.shutdownNow();
    }

    public int tamañoCola()    { return cola.size(); }

    /** Solo para tests — permite observar cuándo se procesa una solicitud. */
    public void setOnProcesado(Consumer<SolicitudReserva> callback) {
        this.onProcesado = callback;
    }
}

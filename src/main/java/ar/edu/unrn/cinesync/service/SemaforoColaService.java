package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.model.SolicitudReserva;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Patrón Productor-Consumidor con tres semáforos explícitos (Jorba/Suppi §5.3).
 *
 * mutex  (1)  — exclusión mutua sobre el buffer circular
 * vacias (10) — slots libres (el productor espera aquí si el buffer está lleno)
 * llenas (0)  — ítems listos para consumir (el consumidor espera aquí si está vacío)
 *
 * Secuencia PRODUCTOR:  vacias.acquire → mutex.acquire → escribir → mutex.release → llenas.release
 * Secuencia CONSUMIDOR: llenas.acquire → mutex.acquire → leer    → mutex.release → vacias.release
 */
@Service
public class SemaforoColaService {

    private static final int CAPACIDAD   = 10;
    private static final int NUM_WORKERS = 2;

    private final Semaphore mutex  = new Semaphore(1);
    private final Semaphore vacias = new Semaphore(CAPACIDAD);
    private final Semaphore llenas = new Semaphore(0);

    private final SolicitudReserva[] buffer = new SolicitudReserva[CAPACIDAD];
    private int producirEn  = 0;
    private int consumirDe  = 0;
    private volatile int itemsEnCola = 0;

    private final AtomicLong totalProducidas         = new AtomicLong(0);
    private final AtomicLong totalConsumidas         = new AtomicLong(0);
    private final AtomicLong totalBloqueosProductor  = new AtomicLong(0);
    private final AtomicLong totalBloqueosConsumidor = new AtomicLong(0);

    private volatile String                    ultimoEvento    = "sin eventos";
    private volatile Consumer<EstadoSemaforos> onCambioEstado  = e -> {};
    private volatile boolean                   activo          = true;

    private ExecutorService workers;

    private final ReservaService reservaService;

    public SemaforoColaService(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    public record EstadoSemaforos(
            int    mutexPermisos,
            int    vaciasPermisos,
            int    llenasPermisos,
            int    itemsEnCola,
            int    capacidad,
            long   totalProducidas,
            long   totalConsumidas,
            long   totalBloqueosProductor,
            long   totalBloqueosConsumidor,
            String ultimoEvento,
            String workerId
    ) {}

    @PostConstruct
    public void iniciar() {
        workers = Executors.newFixedThreadPool(NUM_WORKERS, r -> {
            Thread t = new Thread(r, "worker-" + (int)(Math.random() * 9000 + 1000));
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers.submit(this::loopConsumidor);
        }
    }

    // ── Productor ─────────────────────────────────────────────────────────────

    public void encolar(SolicitudReserva solicitud) {
        try {
            if (vacias.availablePermits() == 0) {
                totalBloqueosProductor.incrementAndGet();
            }
            vacias.acquire();   // espera slot libre; bloquea si el buffer está lleno
            mutex.acquire();    // entra a la sección crítica

            buffer[producirEn] = solicitud;
            producirEn = (producirEn + 1) % CAPACIDAD;
            itemsEnCola++;
            totalProducidas.incrementAndGet();
            ultimoEvento = "PRODUCIDO sala=" + solicitud.salaId()
                         + " butaca=" + solicitud.butacaId();

            mutex.release();    // sale de la sección crítica
            llenas.release();   // señala al consumidor que hay un ítem

            notificarCambio(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Consumidor ────────────────────────────────────────────────────────────

    private void loopConsumidor() {
        String workerId = Thread.currentThread().getName();
        while (activo) {
            try {
                if (llenas.availablePermits() == 0) {
                    totalBloqueosConsumidor.incrementAndGet();
                }
                llenas.acquire();
                mutex.acquire();

                SolicitudReserva solicitud = buffer[consumirDe];
                buffer[consumirDe] = null;
                consumirDe = (consumirDe + 1) % CAPACIDAD;
                itemsEnCola--;
                totalConsumidas.incrementAndGet();
                ultimoEvento = "PROCESANDO sala=" + solicitud.salaId()
                        + " butaca=" + solicitud.butacaId();

                mutex.release();

                // Delay ANTES de vacias.release() — el slot sigue ocupado
                // El productor no puede depositar hasta que liberemos aquí
                notificarCambio(workerId);
                Thread.sleep(3000); // visible en demo

                vacias.release(); // recién acá se libera el slot
                ultimoEvento = "CONSUMIDO sala=" + solicitud.salaId()
                        + " butaca=" + solicitud.butacaId();
                notificarCambio(workerId);

                reservaService.reservar(
                        solicitud.salaId(),
                        solicitud.butacaId(),
                        solicitud.usuarioId()
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ── Estado y callbacks ────────────────────────────────────────────────────

    public EstadoSemaforos getEstado() {
        return new EstadoSemaforos(
                mutex.availablePermits(),
                vacias.availablePermits(),
                llenas.availablePermits(),
                itemsEnCola,
                CAPACIDAD,
                totalProducidas.get(),
                totalConsumidas.get(),
                totalBloqueosProductor.get(),
                totalBloqueosConsumidor.get(),
                ultimoEvento,
                null
        );
    }

    public void setOnCambioEstado(Consumer<EstadoSemaforos> callback) {
        this.onCambioEstado = callback;
    }

    private void notificarCambio(String workerId) {
        onCambioEstado.accept(new EstadoSemaforos(
                mutex.availablePermits(),
                vacias.availablePermits(),
                llenas.availablePermits(),
                itemsEnCola,
                CAPACIDAD,
                totalProducidas.get(),
                totalConsumidas.get(),
                totalBloqueosProductor.get(),
                totalBloqueosConsumidor.get(),
                ultimoEvento,
                workerId
        ));
    }

    @PreDestroy
    public void detener() {
        activo = false;
        workers.shutdownNow();
    }
}

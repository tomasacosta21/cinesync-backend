package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.dto.SseEventDTO;
import ar.edu.unrn.cinesync.model.Butaca;
import ar.edu.unrn.cinesync.model.Sala;
import ar.edu.unrn.cinesync.sse.SseEmitterService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servicio central de reservas. Acá viven los dos mecanismos de concurrencia:
 *
 * 1. ReentrantLock (uno por butaca) — sección crítica
 *    Protege la secuencia completa: leer estado → intentar CAS → notificar SSE.
 *    Sin este lock, dos hilos podrían leer "LIBRE" al mismo tiempo y ambos
 *    intentar el CAS; aunque solo uno gane el CAS, el otro podría notificar
 *    un estado inconsistente. El lock evita ese interleavings.
 *
 *    fairness=true: los hilos esperan en orden FIFO, evitando starvation
 *    (un hilo que espera mucho no es ignorado indefinidamente).
 *
 * 2. AtomicReference<EstadoButaca> (en Butaca) — operación atómica final
 *    El compareAndSet es la última línea de defensa: aunque el lock ya protege
 *    la sección crítica, el CAS garantiza que la transición de estado sea
 *    atómica a nivel de hardware (sin instrucciones de escritura parciales).
 *
 * Ambos mecanismos juntos demuestran la doble capa de protección que
 * existe en sistemas concurrentes robustos (lock para la lógica, CAS para el dato).
 */
@Service
public class ReservaServiceImpl implements ReservaService {

    private final SalaService salaService;
    private final SseEmitterService sseEmitterService;

    /**
     * Mapa de locks: clave = "salaId:butacaId" → ReentrantLock.
     * ConcurrentHashMap + computeIfAbsent garantiza que se cree un solo lock
     * por butaca incluso si dos hilos lo piden al mismo tiempo.
     */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReservaServiceImpl(SalaService salaService, SseEmitterService sseEmitterService) {
        this.salaService       = salaService;
        this.sseEmitterService = sseEmitterService;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String clave(int salaId, String butacaId) {
        return salaId + ":" + butacaId;
    }

    private ReentrantLock lockPara(int salaId, String butacaId) {
        // fair=true: orden FIFO entre hilos en espera
        return locks.computeIfAbsent(clave(salaId, butacaId), k -> new ReentrantLock(true));
    }

    // ─── Operaciones ──────────────────────────────────────────────────────────

    @Override
    public boolean reservar(int salaId, String butacaId, String usuarioId) {
        Sala sala          = salaService.obtenerSala(salaId);
        Butaca butaca      = sala.buscarButaca(butacaId);
        ReentrantLock lock = lockPara(salaId, butacaId);

        lock.lock();          // ← entra a la sección crítica
        try {
            boolean exito = butaca.reservar();   // CAS: LIBRE → RESERVADA
            if (exito) {
                notificar(salaId, butacaId, butaca.getEstado().name());
            }
            return exito;
        } finally {
            lock.unlock();    // ← siempre libera, aunque haya excepción
        }
    }

    @Override
    public boolean confirmar(int salaId, String butacaId) {
        Sala sala          = salaService.obtenerSala(salaId);
        Butaca butaca      = sala.buscarButaca(butacaId);
        ReentrantLock lock = lockPara(salaId, butacaId);

        lock.lock();
        try {
            boolean exito = butaca.confirmar();  // CAS: RESERVADA → OCUPADA
            if (exito) {
                notificar(salaId, butacaId, butaca.getEstado().name());
            }
            return exito;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean liberar(int salaId, String butacaId) {
        Sala sala          = salaService.obtenerSala(salaId);
        Butaca butaca      = sala.buscarButaca(butacaId);
        ReentrantLock lock = lockPara(salaId, butacaId);

        lock.lock();
        try {
            boolean exito = butaca.liberar();    // CAS: RESERVADA → LIBRE
            if (exito) {
                notificar(salaId, butacaId, butaca.getEstado().name());
            }
            return exito;
        } finally {
            lock.unlock();
        }
    }

    // ─── Notificación SSE ─────────────────────────────────────────────────────

    private void notificar(int salaId, String butacaId, String estado) {
        sseEmitterService.broadcast(new SseEventDTO(salaId, butacaId, estado));
    }
}

package ar.edu.unrn.cinesync.simulacion;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Estado en memoria de una simulación (vive en el ConcurrentHashMap del service).
 *
 * CONCURRENCIA:
 * - Los envíos SSE pasan por un executor de un solo hilo propio de la
 *   simulación: los workers solo encolan (costo ~constante e idéntico para
 *   las 3 técnicas, así el broadcast no distorsiona la comparación) y el
 *   orden de los eventos queda garantizado por el hilo único.
 * - Los eventos "clave" (TECNICA_COMPLETADA, SIMULACION_COMPLETA) se bufferean
 *   y se reenvían a los clientes que se suscriben tarde; los BUTACA_ACTUALIZADA
 *   son efímeros (puro feedback visual) y no se replayean.
 */
public class SimulacionState {

    public enum Estado { EN_CURSO, COMPLETADA, ERROR }

    private final String id;
    private final String escenario;
    private final List<Integer> workersEjecutados;

    private volatile Estado estado = Estado.EN_CURSO;
    private volatile String mensajeError;

    private final List<MetricasSimulacion> resultados = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<EventoSimulacion> eventosClave = new CopyOnWriteArrayList<>();

    private final ExecutorService envios;

    public SimulacionState(String id, String escenario, List<Integer> workersEjecutados) {
        this.id = id;
        this.escenario = escenario;
        this.workersEjecutados = workersEjecutados;
        this.envios = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sim-sse-" + id.substring(0, 8));
            t.setDaemon(true);
            return t;
        });
    }

    // ── Suscripción SSE ───────────────────────────────────────────────────

    public SseEmitter suscribir() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()    -> emitters.remove(emitter));
        emitter.onError(e       -> emitters.remove(emitter));

        // Replay de eventos clave + alta del emitter, en el hilo de envíos
        // para no intercalarse con un broadcast en curso.
        envios.submit(() -> {
            for (EventoSimulacion ev : eventosClave) {
                enviar(emitter, ev);
            }
            if (estado == Estado.EN_CURSO) {
                emitters.add(emitter);
            } else {
                emitter.complete();
            }
        });
        return emitter;
    }

    // ── Emisión de eventos ────────────────────────────────────────────────

    /** Evento efímero (BUTACA_ACTUALIZADA): solo a los clientes conectados. */
    public void emitir(EventoSimulacion evento) {
        envios.submit(() -> broadcast(evento));
    }

    /** Evento clave: se buferea para replay y se difunde. */
    public void emitirClave(EventoSimulacion evento) {
        eventosClave.add(evento);
        envios.submit(() -> broadcast(evento));
    }

    private void broadcast(EventoSimulacion evento) {
        for (SseEmitter emitter : emitters) {
            enviar(emitter, evento);
        }
    }

    private void enviar(SseEmitter emitter, EventoSimulacion evento) {
        try {
            emitter.send(SseEmitter.event().name("simulacion").data(evento));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────

    public void agregarResultados(List<MetricasSimulacion> corridas) {
        resultados.addAll(corridas);
    }

    public void completar() {
        estado = Estado.COMPLETADA;
        cerrar();
    }

    public void fallar(String mensaje) {
        estado = Estado.ERROR;
        mensajeError = mensaje;
        cerrar();
    }

    /** Cierra los emitters después de drenar la cola de envíos pendientes. */
    private void cerrar() {
        envios.submit(() -> {
            for (SseEmitter emitter : emitters) {
                try { emitter.complete(); } catch (Exception ignored) {}
            }
            emitters.clear();
        });
        envios.shutdown();
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String getId()                            { return id; }
    public String getEscenario()                     { return escenario; }
    public List<Integer> getWorkersEjecutados()      { return workersEjecutados; }
    public Estado getEstado()                        { return estado; }
    public String getMensajeError()                  { return mensajeError; }
    public List<MetricasSimulacion> getResultados()  { return List.copyOf(resultados); }
}

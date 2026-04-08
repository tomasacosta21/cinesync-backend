package ar.edu.unrn.cinesync.sse;

import ar.edu.unrn.cinesync.dto.SseEventDTO;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestiona los clientes SSE conectados y les envía eventos de cambio de estado.
 *
 * DECISIONES DE DISEÑO:
 *
 * 1. CopyOnWriteArraySet — thread-safe para el patrón muchas-lecturas/pocas-escrituras.
 *    Cada iteración del broadcast opera sobre un snapshot inmutable, por lo que
 *    agregar/quitar emitters durante el broadcast no causa ConcurrentModificationException.
 *
 * 2. Broadcast asíncrono — cada envío corre en un hilo del pool dedicado.
 *    Esto evita que un emitter lento o muerto bloquee el broadcast al resto
 *    de clientes, lo que sería un problema bajo carga alta en producción.
 *
 * 3. Broken pipe — cuando un cliente se desconecta abruptamente, el send()
 *    lanza IOException. La capturamos, marcamos el emitter como muerto y lo
 *    removemos del set. El error es esperado y no indica falla del sistema.
 *    Los logs de Tomcat para este error están suprimidos en application.properties.
 */
@Service
public class SseEmitterService {

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();

    /**
     * Pool dedicado al broadcast SSE.
     * Separado del thread pool de Tomcat para no saturar los hilos HTTP
     * cuando hay muchos clientes conectados simultáneamente.
     */
    private final ExecutorService broadcastPool =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "sse-broadcast");
                t.setDaemon(true);
                return t;
            });

    public SseEmitter registrar() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()    -> emitters.remove(emitter));
        emitter.onError(e       -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * Envía el evento a todos los clientes conectados de forma asíncrona.
     * Los emitters muertos se detectan por IOException y se eliminan del set.
     */
    public void broadcast(SseEventDTO evento) {
        for (SseEmitter emitter : emitters) {
            broadcastPool.submit(() -> enviar(emitter, evento));
        }
    }

    private void enviar(SseEmitter emitter, SseEventDTO evento) {
        try {
            emitter.send(SseEmitter.event()
                    .name("butaca-actualizada")
                    .data(evento));
        } catch (IOException e) {
            // Cliente desconectado — limpieza silenciosa
            emitters.remove(emitter);
        } catch (Exception e) {
            // Cualquier otro error inesperado — también limpiamos
            emitters.remove(emitter);
        }
    }

    public int clientesConectados() { return emitters.size(); }

    @PreDestroy
    public void cerrar() {
        broadcastPool.shutdownNow();
    }
}
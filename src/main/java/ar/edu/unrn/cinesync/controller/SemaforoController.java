package ar.edu.unrn.cinesync.controller;

import ar.edu.unrn.cinesync.model.SolicitudReserva;
import ar.edu.unrn.cinesync.service.SemaforoColaService;
import ar.edu.unrn.cinesync.service.SemaforoColaService.EstadoSemaforos;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * API REST del sistema de semáforos.
 *
 * Endpoints:
 *   GET  /api/semaforos/estado        → snapshot del estado de los tres semáforos
 *   POST /api/semaforos/encolar       → encola una solicitud individual (productor)
 *   POST /api/semaforos/encolar-lote  → lanza N hilos virtuales que encolan en paralelo
 *   GET  /api/semaforos/eventos       → SSE; emite "estado-semaforos" en cada cambio de buffer
 */
@RestController
@RequestMapping("/api/semaforos")
public class SemaforoController {

    private final SemaforoColaService semaforoColaService;

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final ExecutorService broadcastPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "semaforo-sse");
        t.setDaemon(true);
        return t;
    });

    public SemaforoController(SemaforoColaService semaforoColaService) {
        this.semaforoColaService = semaforoColaService;
    }

    @PostConstruct
    public void configurarCallback() {
        semaforoColaService.setOnCambioEstado(estado ->
            emitters.forEach(emitter ->
                broadcastPool.submit(() -> enviarEstado(emitter, estado))
            )
        );
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping("/estado")
    public EstadoSemaforos estado() {
        return semaforoColaService.getEstado();
    }

    @PostMapping("/encolar")
    public ResponseEntity<String> encolar(@RequestBody EncolarRequest request) {
        semaforoColaService.encolar(new SolicitudReserva(
                request.usuarioId(),
                request.salaId(),
                request.butacaId(),
                Instant.now()
        ));
        return ResponseEntity.accepted().body("encolado");
    }

    /**
     * Lanza {@code request.cantidad()} hilos virtuales (Project Loom), cada uno
     * encolando una solicitud independiente. Demuestra que el semáforo vacias
     * bloquea productores cuando el buffer está lleno, sin desperdiciar CPU.
     */
    @PostMapping("/encolar-lote")
    public ResponseEntity<String> encolarLote(@RequestBody LoteRequest request) {
        for (int i = 0; i < request.cantidad(); i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> {
                char fila    = (char) ('A' + (idx / 12) % 26);
                int  columna = (idx % 12) + 1;
                semaforoColaService.encolar(new SolicitudReserva(
                        "lote-usuario-" + idx,
                        request.salaId(),
                        fila + String.valueOf(columna),
                        Instant.now()
                ));
            });
        }
        return ResponseEntity.accepted()
                .body("lote de " + request.cantidad() + " solicitudes encoladas");
    }

    @GetMapping(value = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()    -> emitters.remove(emitter));
        emitter.onError(e       -> emitters.remove(emitter));
        return emitter;
    }

    // ── SSE helper ────────────────────────────────────────────────────────────

    private void enviarEstado(SseEmitter emitter, EstadoSemaforos estado) {
        try {
            emitter.send(SseEmitter.event()
                    .name("estado-semaforos")
                    .data(estado));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }

    @PreDestroy
    public void cerrar() {
        broadcastPool.shutdownNow();
    }

    // ── DTOs internos ─────────────────────────────────────────────────────────

    public record EncolarRequest(int salaId, String butacaId, String usuarioId) {}
    public record LoteRequest(int cantidad, int salaId) {}
}

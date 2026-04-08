package ar.edu.unrn.cinesync.controller;

import ar.edu.unrn.cinesync.sse.SseEmitterService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Endpoint SSE al que se suscribe el frontend Angular.
 * Cada pestaña/cliente abre una conexión GET a este endpoint y recibe
 * eventos en tiempo real cada vez que una butaca cambia de estado.
 */
@RestController
@RequestMapping("/api/eventos")
public class SseController {

    private final SseEmitterService sseEmitterService;

    public SseController(SseEmitterService sseEmitterService) {
        this.sseEmitterService = sseEmitterService;
    }

    /**
     * GET /api/eventos/sala/{salaId}
     * Registra al cliente y devuelve un SseEmitter de vida infinita.
     * Spring mantiene la conexión HTTP abierta hasta que el cliente se desconecta.
     */
    @GetMapping("/sala/{salaId}")
    public SseEmitter suscribir(@PathVariable int salaId) {
        return sseEmitterService.registrar();
    }

    /** GET /api/eventos/clientes — útil para debugging en dev */
    @GetMapping("/clientes")
    public int clientesConectados() {
        return sseEmitterService.clientesConectados();
    }
}

package ar.edu.unrn.cinesync.controller;

import ar.edu.siglo21.cinesync.dto.*;
import ar.edu.unrn.cinesync.model.Sala;
import ar.edu.siglo21.cinesync.model.SolicitudReserva;
import ar.edu.unrn.cinesync.service.ColaReservasService;
import ar.edu.unrn.cinesync.service.ReservaService;
import ar.edu.unrn.cinesync.service.SalaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * API REST del sistema de reservas.
 *
 * Endpoints:
 *   GET  /api/salas                          → lista las 3 salas con butacas
 *   GET  /api/salas/{id}                     → detalle de una sala
 *   POST /api/salas/{id}/reservar            → encola solicitud (productor)
 *   POST /api/salas/{id}/confirmar/{butaca}  → RESERVADA → OCUPADA
 *   POST /api/salas/{id}/liberar/{butaca}    → RESERVADA → LIBRE
 */
@RestController
@RequestMapping("/api/salas")
public class SalaController {

    private final SalaService salaService;
    private final ReservaService reservaService;
    private final ColaReservasService colaReservasService;

    public SalaController(SalaService salaService,
                          ReservaService reservaService,
                          ColaReservasService colaReservasService) {
        this.salaService          = salaService;
        this.reservaService       = reservaService;
        this.colaReservasService  = colaReservasService;
    }

    /** Lista todas las salas con su estado actual de butacas. */
    @GetMapping
    public List<SalaDTO> listarSalas() {
        return salaService.listarSalas().stream()
                .map(this::toDTO)
                .toList();
    }

    /** Detalle completo de una sala. */
    @GetMapping("/{id}")
    public SalaDTO obtenerSala(@PathVariable int id) {
        return toDTO(salaService.obtenerSala(id));
    }

    /**
     * Reserva una butaca encolando la solicitud (patrón Productor).
     * Responde HTTP 202 Accepted inmediatamente — el procesamiento es asíncrono.
     */
    @PostMapping("/{id}/reservar")
    public ResponseEntity<ReservaResponse> reservar(
            @PathVariable int id,
            @RequestBody ReservaRequest request) {

        colaReservasService.encolar(
                new SolicitudReserva(request.usuarioId(), id, request.butacaId(), Instant.now())
        );

        return ResponseEntity.accepted().body(
                new ReservaResponse(true, "Solicitud encolada", request.butacaId(), "PENDIENTE")
        );
    }

    /**
     * Confirma el pago y ocupa definitivamente la butaca (RESERVADA → OCUPADA).
     */
    @PostMapping("/{id}/confirmar/{butacaId}")
    public ResponseEntity<ReservaResponse> confirmar(
            @PathVariable int id,
            @PathVariable String butacaId) {

        boolean exito = reservaService.confirmar(id, butacaId);
        String estado = salaService.obtenerSala(id).buscarButaca(butacaId).getEstado().name();

        return ResponseEntity.ok(new ReservaResponse(
                exito,
                exito ? "Butaca confirmada" : "No se pudo confirmar (estado incorrecto)",
                butacaId,
                estado
        ));
    }

    /**
     * Libera una reserva (RESERVADA → LIBRE) por timeout o cancelación del usuario.
     */
    @PostMapping("/{id}/liberar/{butacaId}")
    public ResponseEntity<ReservaResponse> liberar(
            @PathVariable int id,
            @PathVariable String butacaId) {

        boolean exito = reservaService.liberar(id, butacaId);
        String estado = salaService.obtenerSala(id).buscarButaca(butacaId).getEstado().name();

        return ResponseEntity.ok(new ReservaResponse(
                exito,
                exito ? "Butaca liberada" : "No se pudo liberar (estado incorrecto)",
                butacaId,
                estado
        ));
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────

    private SalaDTO toDTO(Sala sala) {
        List<ButacaDTO> butacasDTO = sala.getButacas().stream()
                .map(b -> new ButacaDTO(b.getId(), b.getFila(), b.getColumna(), b.getEstado().name()))
                .toList();
        return new SalaDTO(sala.getId(), sala.getNombre(), sala.getFilas(), sala.getColumnas(), butacasDTO);
    }
}

package ar.edu.unrn.cinesync.controller;

import ar.edu.unrn.cinesync.dto.*;
import ar.edu.unrn.cinesync.model.Butaca;
import ar.edu.unrn.cinesync.model.Sala;
import ar.edu.unrn.cinesync.model.SolicitudReserva;
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
 *   GET  /api/salas                             → lista las 3 salas
 *   GET  /api/salas/{id}                        → detalle de sala
 *   POST /api/salas/{id}/reservar               → encola solicitud (patrón Productor)
 *   POST /api/salas/{id}/reservar-directo       → reserva síncrona con resultado del CAS
 *   POST /api/salas/{id}/confirmar/{butaca}     → RESERVADA → OCUPADA
 *   POST /api/salas/{id}/liberar/{butaca}       → RESERVADA → LIBRE
 *
 * ¿Por qué dos endpoints de reserva?
 *
 * /reservar          → usa la ColaReservasService (patrón Productor-Consumidor).
 *                      Responde HTTP 202 inmediatamente sin conocer el resultado del CAS.
 *                      Usado por la simulación automática (bots).
 *
 * /reservar-directo  → llama a ReservaService directamente de forma síncrona.
 *                      Retorna el resultado REAL del CAS: exitoso=true si ganó
 *                      la carrera, exitoso=false si otro hilo se adelantó.
 *                      Usado por usuarios reales para mostrar el toast de victoria/derrota.
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
        this.salaService         = salaService;
        this.reservaService      = reservaService;
        this.colaReservasService = colaReservasService;
    }

    @GetMapping
    public List<SalaDTO> listarSalas() {
        return salaService.listarSalas().stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    public SalaDTO obtenerSala(@PathVariable int id) {
        return toDTO(salaService.obtenerSala(id));
    }

    /**
     * Reserva asíncrona vía cola — para la simulación automática (bots).
     * Siempre retorna exitoso=true porque solo encola, no ejecuta el CAS.
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
     * Reserva SÍNCRONA con resultado real del CAS.
     *
     * Este endpoint es la clave de la demostración de race condition:
     * cuando dos usuarios llaman simultáneamente con la misma butaca,
     * el ReentrantLock garantiza que solo uno ejecuta el CAS exitosamente.
     * El resultado (true/false) se retorna en la misma respuesta HTTP,
     * permitiendo al frontend mostrar el toast de victoria o derrota.
     *
     * HTTP 200 exitoso=true  → el usuario ganó la carrera (LIBRE → RESERVADA)
     * HTTP 200 exitoso=false → otro hilo se adelantó (butaca ya no estaba LIBRE)
     */
    @PostMapping("/{id}/reservar-directo")
    public ResponseEntity<ReservaResponse> reservarDirecto(
            @PathVariable int id,
            @RequestBody ReservaRequest request) {

        boolean exito = reservaService.reservar(id, request.butacaId(), request.usuarioId());
        Sala sala     = salaService.obtenerSala(id);
        String estado = sala.buscarButaca(request.butacaId()).getEstado().name();

        String mensaje = exito
                ? "¡Reserva exitosa! Sos el primero."
                : "Otra persona reservó esta butaca antes que vos.";

        return ResponseEntity.ok(
                new ReservaResponse(exito, mensaje, request.butacaId(), estado)
        );
    }

    @PostMapping("/{id}/confirmar/{butacaId}")
    public ResponseEntity<ReservaResponse> confirmar(
            @PathVariable int id,
            @PathVariable String butacaId) {

        boolean exito = reservaService.confirmar(id, butacaId);
        String estado = salaService.obtenerSala(id).buscarButaca(butacaId).getEstado().name();

        return ResponseEntity.ok(new ReservaResponse(
                exito,
                exito ? "Butaca confirmada" : "No se pudo confirmar",
                butacaId, estado
        ));
    }

    @PostMapping("/{id}/liberar/{butacaId}")
    public ResponseEntity<ReservaResponse> liberar(
            @PathVariable int id,
            @PathVariable String butacaId) {

        boolean exito = reservaService.liberar(id, butacaId);
        String estado = salaService.obtenerSala(id).buscarButaca(butacaId).getEstado().name();

        return ResponseEntity.ok(new ReservaResponse(
                exito,
                exito ? "Butaca liberada" : "No se pudo liberar",
                butacaId, estado
        ));
    }

    private SalaDTO toDTO(Sala sala) {
        List<ButacaDTO> butacasDTO = sala.getButacas().stream()
                .map(b -> new ButacaDTO(b.getId(), b.getFila(), b.getColumna(), b.getEstado().name()))
                .toList();
        return new SalaDTO(sala.getId(), sala.getNombre(), sala.getFilas(), sala.getColumnas(), butacasDTO);
    }
}
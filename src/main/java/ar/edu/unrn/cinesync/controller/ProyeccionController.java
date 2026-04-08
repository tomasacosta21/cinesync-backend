package ar.edu.unrn.cinesync.controller;

import ar.edu.unrn.cinesync.dto.ProyeccionDTO;
import ar.edu.unrn.cinesync.dto.ProyeccionRequest;
import ar.edu.unrn.cinesync.model.Pelicula;
import ar.edu.unrn.cinesync.model.Proyeccion;
import ar.edu.unrn.cinesync.model.Sala;
import ar.edu.unrn.cinesync.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ABM de proyecciones + activación de sala + control de simulación.
 *
 * GET    /api/proyecciones                      → todas
 * GET    /api/proyecciones/sala/{salaId}        → por sala
 * GET    /api/proyecciones/{id}                 → detalle
 * POST   /api/proyecciones                      → crear
 * PUT    /api/proyecciones/{id}                 → actualizar
 * POST   /api/proyecciones/{id}/activar         → activa en sala + resetea butacas
 * DELETE /api/proyecciones/{id}                 → cancelar
 *
 * POST   /api/proyecciones/simulacion/{salaId}/iniciar  → inicia simulación
 * POST   /api/proyecciones/simulacion/{salaId}/detener  → detiene simulación
 * GET    /api/proyecciones/simulacion/{salaId}/estado   → estado actual
 */
@RestController
@RequestMapping("/api/proyecciones")
public class ProyeccionController {

    private final ProyeccionService proyeccionService;
    private final PeliculaService   peliculaService;
    private final SalaService       salaService;
    private final SimulacionService simulacionService;

    public ProyeccionController(ProyeccionService proyeccionService,
                                PeliculaService peliculaService,
                                SalaService salaService,
                                SimulacionService simulacionService) {
        this.proyeccionService = proyeccionService;
        this.peliculaService   = peliculaService;
        this.salaService       = salaService;
        this.simulacionService = simulacionService;
    }

    // ── CRUD Proyecciones ─────────────────────────────────────

    @GetMapping
    public List<ProyeccionDTO> listarTodas() {
        return proyeccionService.listarTodas().stream().map(this::toDTO).toList();
    }

    @GetMapping("/sala/{salaId}")
    public List<ProyeccionDTO> listarPorSala(@PathVariable int salaId) {
        return proyeccionService.listarPorSala(salaId).stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    public ProyeccionDTO obtener(@PathVariable String id) {
        return toDTO(proyeccionService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<ProyeccionDTO> crear(@RequestBody ProyeccionRequest req) {
        Proyeccion p = proyeccionService.crear(
                req.salaId(), req.peliculaId(), req.fechaHora(), req.precioBase()
        );
        return ResponseEntity.status(201).body(toDTO(p));
    }

    @PutMapping("/{id}")
    public ProyeccionDTO actualizar(@PathVariable String id,
                                    @RequestBody ProyeccionRequest req) {
        return toDTO(proyeccionService.actualizar(
                id, req.peliculaId(), req.fechaHora(), req.precioBase()
        ));
    }

    /**
     * Activa una proyección: resetea las butacas de la sala y notifica via SSE.
     */
    @PostMapping("/{id}/activar")
    public ResponseEntity<ProyeccionDTO> activar(@PathVariable String id) {
        proyeccionService.activar(id);
        return ResponseEntity.ok(toDTO(proyeccionService.obtener(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable String id) {
        proyeccionService.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    // ── Control de simulación ─────────────────────────────────

    @PostMapping("/simulacion/{salaId}/iniciar")
    public ResponseEntity<Map<String, Object>> iniciarSimulacion(@PathVariable int salaId) {
        long duracion = simulacionService.iniciar(salaId);
        return ResponseEntity.ok(Map.of(
                "activa",              true,
                "salaId",             salaId,
                "duracionSegundos",   duracion,
                "mensaje",            "Simulación iniciada para sala " + salaId
        ));
    }

    @PostMapping("/simulacion/{salaId}/detener")
    public ResponseEntity<Map<String, Object>> detenerSimulacion(@PathVariable int salaId) {
        simulacionService.detener(salaId);
        return ResponseEntity.ok(Map.of(
                "activa",  false,
                "salaId",  salaId,
                "mensaje", "Simulación detenida para sala " + salaId
        ));
    }

    @GetMapping("/simulacion/{salaId}/estado")
    public Map<String, Object> estadoSimulacion(@PathVariable int salaId) {
        return Map.of(
                "activa",  simulacionService.estaActiva(salaId),
                "salaId",  salaId
        );
    }

    // ── Mapper ────────────────────────────────────────────────

    private ProyeccionDTO toDTO(Proyeccion p) {
        Pelicula pelicula = peliculaService.obtener(p.getPeliculaId());
        Sala sala         = salaService.obtenerSala(p.getSalaId());
        return new ProyeccionDTO(
                p.getId(),
                p.getSalaId(),
                sala.getNombre(),
                p.getPeliculaId(),
                pelicula.getTitulo(),
                pelicula.getImagen(),
                pelicula.getClasificacion(),
                pelicula.getDuracionMinutos(),
                p.getFechaHora(),
                p.getPrecioBase(),
                p.getEstado().name()
        );
    }
}

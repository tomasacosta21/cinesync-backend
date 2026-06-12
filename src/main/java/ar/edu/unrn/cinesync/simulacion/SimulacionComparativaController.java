package ar.edu.unrn.cinesync.simulacion;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * API REST de la simulación comparativa de técnicas de sincronización.
 *
 *   POST /api/simulacion/iniciar             → 202 + simulacionId
 *   GET  /api/simulacion/progreso/{id}       → SSE con eventos en tiempo real
 *   GET  /api/simulacion/resultados/{id}     → métricas completas en JSON
 *   GET  /api/simulacion/exportar/{id}       → descarga .xlsx (Apache POI)
 */
@RestController
@RequestMapping("/api/simulacion")
public class SimulacionComparativaController {

    private final SimulacionComparativaService simulacionService;
    private final ExportacionExcelService exportacionService;

    public SimulacionComparativaController(SimulacionComparativaService simulacionService,
                                           ExportacionExcelService exportacionService) {
        this.simulacionService = simulacionService;
        this.exportacionService = exportacionService;
    }

    /**
     * Body: { "escenario": "ALTA_CONTENCION", "workers": 4 }
     * "workers" puede omitirse (null) → barrido completo 1, 2, 4, 8,
     * necesario para la hoja de Speedup del Excel.
     */
    @PostMapping("/iniciar")
    public ResponseEntity<IniciarResponse> iniciar(@RequestBody IniciarRequest request) {
        SimulacionState state = simulacionService.iniciar(request.escenario(), request.workers());
        return ResponseEntity.accepted().body(new IniciarResponse(
                state.getId(), state.getEscenario(), state.getWorkersEjecutados()));
    }

    @GetMapping("/progreso/{simulacionId}")
    public SseEmitter progreso(@PathVariable String simulacionId) {
        return simulacionService.obtener(simulacionId).suscribir();
    }

    @GetMapping("/resultados/{simulacionId}")
    public ResultadosResponse resultados(@PathVariable String simulacionId) {
        SimulacionState state = simulacionService.obtener(simulacionId);
        return new ResultadosResponse(
                state.getId(),
                state.getEstado().name(),
                state.getEscenario(),
                state.getWorkersEjecutados(),
                state.getMensajeError(),
                state.getResultados());
    }

    @GetMapping("/exportar/{simulacionId}")
    public ResponseEntity<byte[]> exportar(@PathVariable String simulacionId) {
        SimulacionState state = simulacionService.obtener(simulacionId);
        if (state.getEstado() != SimulacionState.Estado.COMPLETADA) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        byte[] excel = exportacionService.generar(state);
        String nombre = "cinesync-simulacion-%s.xlsx".formatted(simulacionId.substring(0, 8));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarArgumentoInvalido(IllegalArgumentException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Simulación no encontrada")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error", String.valueOf(e.getMessage())));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public record IniciarRequest(String escenario, Integer workers) {}

    public record IniciarResponse(String simulacionId, String escenario, List<Integer> workers) {}

    public record ResultadosResponse(String simulacionId, String estado, String escenario,
                                     List<Integer> workers, String mensajeError,
                                     List<MetricasSimulacion> resultados) {}
}

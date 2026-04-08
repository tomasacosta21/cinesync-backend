package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.dto.SseEventDTO;
import ar.edu.unrn.cinesync.model.Proyeccion;
import ar.edu.unrn.cinesync.model.Sala;
import ar.edu.unrn.cinesync.sse.SseEmitterService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProyeccionServiceImpl implements ProyeccionService {

    private final Map<String, Proyeccion> proyecciones = new ConcurrentHashMap<>();
    private final SalaService salaService;
    private final PeliculaService peliculaService;
    private final SseEmitterService sseEmitterService;

    public ProyeccionServiceImpl(SalaService salaService,
                                 PeliculaService peliculaService,
                                 SseEmitterService sseEmitterService) {
        this.salaService       = salaService;
        this.peliculaService   = peliculaService;
        this.sseEmitterService = sseEmitterService;
        seedData();
    }

    // ── Seed de proyecciones de ejemplo ───────────────────────

    private void seedData() {
        List<String> ids = peliculaService.listar().stream()
                .map(p -> p.getId())
                .toList();

        if (ids.size() < 3) return;

        LocalDateTime base = LocalDateTime.now().withHour(18).withMinute(0).withSecond(0).withNano(0);

        // Sala 1 — Estrenos: 3 funciones de las 2 primeras películas
        guardar(new Proyeccion(1, ids.get(0), base,                        2500));
        guardar(new Proyeccion(1, ids.get(0), base.plusHours(3),           2500));
        guardar(new Proyeccion(1, ids.get(1), base.plusDays(1),            2800));

        // Sala 2 — Nueva: 2 funciones de otra película
        guardar(new Proyeccion(2, ids.get(2), base,                        2200));
        guardar(new Proyeccion(2, ids.get(3), base.plusDays(1),            2200));

        // Sala 3 — Clásica: clásicos a precio reducido
        guardar(new Proyeccion(3, ids.get(4), base.minusHours(2),          1500));
        guardar(new Proyeccion(3, ids.get(5), base.plusDays(2),            1500));
    }

    private void guardar(Proyeccion p) {
        proyecciones.put(p.getId(), p);
    }

    // ── CRUD ──────────────────────────────────────────────────

    @Override
    public List<Proyeccion> listarPorSala(int salaId) {
        return proyecciones.values().stream()
                .filter(p -> p.getSalaId() == salaId
                          && p.getEstado() != Proyeccion.Estado.CANCELADA)
                .sorted((a, b) -> a.getFechaHora().compareTo(b.getFechaHora()))
                .toList();
    }

    @Override
    public List<Proyeccion> listarTodas() {
        return List.copyOf(proyecciones.values());
    }

    @Override
    public Proyeccion obtener(String id) {
        Proyeccion p = proyecciones.get(id);
        if (p == null) throw new IllegalArgumentException("Proyección no encontrada: " + id);
        return p;
    }

    @Override
    public Proyeccion crear(int salaId, String peliculaId,
                            LocalDateTime fechaHora, double precioBase) {
        salaService.obtenerSala(salaId);       // valida que la sala existe
        peliculaService.obtener(peliculaId);   // valida que la película existe
        Proyeccion p = new Proyeccion(salaId, peliculaId, fechaHora, precioBase);
        guardar(p);
        return p;
    }

    @Override
    public Proyeccion actualizar(String id, String peliculaId,
                                 LocalDateTime fechaHora, double precioBase) {
        Proyeccion p = obtener(id);
        if (peliculaId != null) {
            peliculaService.obtener(peliculaId);
            p.setPeliculaId(peliculaId);
        }
        if (fechaHora != null)  p.setFechaHora(fechaHora);
        if (precioBase > 0)     p.setPrecioBase(precioBase);
        return p;
    }

    @Override
    public void cancelar(String id) {
        Proyeccion p = obtener(id);
        p.setEstado(Proyeccion.Estado.CANCELADA);
    }

    @Override
    public void eliminar(String id) {
        if (proyecciones.remove(id) == null) {
            throw new IllegalArgumentException("Proyección no encontrada: " + id);
        }
    }

    /**
     * Activa una proyección en su sala:
     *  1. Resetea todas las butacas de la sala a LIBRE
     *  2. Registra la proyección como activa de esa sala
     *  3. Notifica via SSE a todos los clientes del reset
     */
    @Override
    public void activar(String proyeccionId) {
        Proyeccion proyeccion = obtener(proyeccionId);
        Sala sala = salaService.obtenerSala(proyeccion.getSalaId());

        // Resetea butacas e instala la proyección activa
        sala.activarProyeccion(proyeccionId);
        proyeccion.setEstado(Proyeccion.Estado.EN_CURSO);

        // Notifica a todos los clientes SSE que la sala fue reseteada
        sala.getButacas().forEach(b ->
            sseEmitterService.broadcast(
                new SseEventDTO(sala.getId(), b.getId(), b.getEstado().name())
            )
        );
    }
}

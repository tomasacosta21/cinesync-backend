package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.model.Butaca;
import ar.edu.unrn.cinesync.model.EstadoButaca;
import ar.edu.unrn.cinesync.model.Sala;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simula actividad de reservas automáticas por sala.
 * Se activa al entrar a la vista de sala y se detiene al salir.
 *
 * Tasas de reserva configuradas por sala:
 *   Sala 1 — Estrenos:  3 butacas por minuto  (1 cada 20s)
 *   Sala 2 — Nueva:     1 butaca por minuto    (1 cada 60s)
 *   Sala 3 — Clásica:   1 butaca cada 2 min    (1 cada 120s)
 *
 * Timer global de simulación: 5 minutos por sesión.
 *
 * CONCURRENCIA: cada sala tiene su propio ScheduledExecutorService,
 * lo que demuestra concurrencia real entre salas — los schedulers
 * de las 3 salas corren en hilos independientes simultáneamente.
 */
@Service
public class SimulacionService {

    // Intervalo en segundos entre cada reserva automática, por sala
    private static final Map<Integer, Long> INTERVALO_SEGUNDOS = Map.of(
            1, 20L,   // 3 por minuto → 1 cada 20s
            2, 60L,   // 1 por minuto
            3, 120L   // 1 cada 2 minutos
    );

    private static final long DURACION_TOTAL_SEGUNDOS = 300L; // 5 minutos

    private final SalaService salaService;
    private final ReservaService reservaService;

    // Un scheduler por sala
    private final Map<Integer, ScheduledExecutorService> schedulers = new ConcurrentHashMap<>();
    private final Map<Integer, ScheduledFuture<?>>       tareas     = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicBoolean>            activas    = new ConcurrentHashMap<>();

    public SimulacionService(SalaService salaService, ReservaService reservaService) {
        this.salaService    = salaService;
        this.reservaService = reservaService;
    }

    /**
     * Inicia la simulación para una sala específica.
     * Si ya estaba corriendo para esa sala, la reinicia desde cero.
     *
     * @param salaId ID de la sala (1, 2 o 3)
     * @return segundos totales de la simulación (siempre DURACION_TOTAL_SEGUNDOS)
     */
    public long iniciar(int salaId) {
        detener(salaId);  // limpia cualquier simulación previa de esta sala

        salaService.obtenerSala(salaId); // valida que la sala exista

        long intervalo = INTERVALO_SEGUNDOS.getOrDefault(salaId, 60L);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulacion-sala-" + salaId);
            t.setDaemon(true);
            return t;
        });

        AtomicBoolean activa = new AtomicBoolean(true);
        activas.put(salaId, activa);
        schedulers.put(salaId, scheduler);

        // Tarea periódica: reserva una butaca libre al azar
        ScheduledFuture<?> tarea = scheduler.scheduleAtFixedRate(
                () -> reservarButacaAleatoria(salaId, activa),
                intervalo,       // primera ejecución después de 1 intervalo
                intervalo,
                TimeUnit.SECONDS
        );
        tareas.put(salaId, tarea);

        // Tarea de detención automática al llegar a los 5 minutos
        scheduler.schedule(() -> detener(salaId),
                DURACION_TOTAL_SEGUNDOS, TimeUnit.SECONDS);

        return DURACION_TOTAL_SEGUNDOS;
    }

    /**
     * Detiene la simulación de una sala y libera sus recursos.
     */
    public void detener(int salaId) {
        AtomicBoolean activa = activas.get(salaId);
        if (activa != null) activa.set(false);

        ScheduledFuture<?> tarea = tareas.remove(salaId);
        if (tarea != null) tarea.cancel(false);

        ScheduledExecutorService scheduler = schedulers.remove(salaId);
        if (scheduler != null) scheduler.shutdownNow();

        activas.remove(salaId);
    }

    /**
     * Devuelve si la simulación está activa para una sala.
     */
    public boolean estaActiva(int salaId) {
        AtomicBoolean activa = activas.get(salaId);
        return activa != null && activa.get();
    }

    // ── Lógica de reserva aleatoria ───────────────────────────

    private void reservarButacaAleatoria(int salaId, AtomicBoolean activa) {
        if (!activa.get()) return;

        try {
            Sala sala = salaService.obtenerSala(salaId);

            // Filtra butacas libres y elige una al azar
            List<Butaca> libres = sala.getButacas().stream()
                    .filter(b -> b.getEstado() == EstadoButaca.LIBRE)
                    .toList();

            if (libres.isEmpty()) {
                detener(salaId);  // sala llena → detiene la simulación
                return;
            }

            Butaca elegida = libres.get(ThreadLocalRandom.current().nextInt(libres.size()));

            // Reserva y confirma simulando un usuario del sistema
            boolean reservada = reservaService.reservar(salaId, elegida.getId(), "simulacion-bot");
            if (reservada) {
                // Confirma después de un breve delay para que el estado RESERVADA sea visible
                Thread.sleep(800);
                reservaService.confirmar(salaId, elegida.getId());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Log silencioso — la simulación continúa aunque falle una reserva puntual
        }
    }

    @PreDestroy
    public void detenerTodo() {
        new HashSet<>(schedulers.keySet()).forEach(this::detener);
    }

    public long getDuracionTotalSegundos() { return DURACION_TOTAL_SEGUNDOS; }
}

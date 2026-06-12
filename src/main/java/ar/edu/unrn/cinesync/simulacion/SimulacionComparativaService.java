package ar.edu.unrn.cinesync.simulacion;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orquesta la simulación comparativa: ejecuta las 3 técnicas de sincronización
 * en forma secuencial sobre una réplica independiente de Sala Estrenos
 * (120 butacas, 10x12), con reset completo entre técnicas y repeticiones.
 *
 * DISEÑO DE LA MEDICIÓN:
 * - Warmup JVM: antes de medir cada técnica se ejecutan 50 solicitudes que se
 *   descartan, para que el JIT compile los caminos calientes.
 * - Reproducibilidad: la secuencia de butacas objetivo se genera con una
 *   semilla fija por (escenario, repetición) — las 3 técnicas reciben
 *   exactamente la misma carga.
 * - Arranque sincronizado: los W workers esperan en un CountDownLatch y
 *   arrancan todos juntos; el wall-clock se mide desde la apertura del latch
 *   hasta que el último worker termina.
 * - Los workers toman solicitudes de un índice atómico compartido
 *   (work-stealing simple), igual para todas las técnicas.
 * - Los eventos SSE de butaca se emiten solo durante la repetición 1 de cada
 *   bloque (las 5 repeticiones son idénticas visualmente) y de forma
 *   asíncrona, para que el broadcast no distorsione las métricas.
 */
@Service
public class SimulacionComparativaService {

    /** Réplica de Sala Estrenos: 10 filas x 12 columnas = 120 butacas. */
    private static final int FILAS = 10;
    private static final int COLUMNAS = 12;

    private static final int SOLICITUDES_WARMUP = 50;
    private static final int REPETICIONES = 5;
    private static final long SEMILLA_BASE = 42L;
    private static final List<Integer> WORKERS_VALIDOS = List.of(1, 2, 4, 8);

    /** Pausa entre técnicas: deja correr la animación de reset del frontend
     *  y le da un respiro al GC antes de la siguiente medición. */
    private static final long PAUSA_ENTRE_TECNICAS_MS = 1200;

    /** Espera inicial para que el cliente alcance a conectar el SSE de progreso. */
    private static final long ESPERA_CONEXION_SSE_MS = 500;

    private final Map<String, SimulacionState> simulaciones = new ConcurrentHashMap<>();

    private final ExecutorService orquestador = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "simulacion-comparativa");
        t.setDaemon(true);
        return t;
    });

    // ── API pública ───────────────────────────────────────────────────────

    /**
     * Lanza una simulación en un hilo propio (no bloquea el request HTTP).
     *
     * @param escenarioStr BAJA_CONTENCION o ALTA_CONTENCION
     * @param workers      1/2/4/8, o null para el barrido completo 1→2→4→8
     *                     (necesario para la hoja de Speedup del Excel)
     * @return simulacionId para seguir el progreso por SSE
     */
    public SimulacionState iniciar(String escenarioStr, Integer workers) {
        EscenarioSimulacion escenario;
        try {
            escenario = EscenarioSimulacion.valueOf(escenarioStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Escenario inválido: '%s'. Valores: BAJA_CONTENCION, ALTA_CONTENCION"
                            .formatted(escenarioStr));
        }
        if (workers != null && !WORKERS_VALIDOS.contains(workers)) {
            throw new IllegalArgumentException(
                    "Workers inválido: %d. Valores: 1, 2, 4, 8 (u omitir para barrido completo)"
                            .formatted(workers));
        }

        List<Integer> listaWorkers = workers == null ? WORKERS_VALIDOS : List.of(workers);
        String id = UUID.randomUUID().toString();
        SimulacionState state = new SimulacionState(id, escenario.name(), listaWorkers);
        simulaciones.put(id, state);

        orquestador.submit(() -> ejecutarSimulacion(state, escenario, listaWorkers));
        return state;
    }

    public SimulacionState obtener(String simulacionId) {
        SimulacionState state = simulaciones.get(simulacionId);
        if (state == null) {
            throw new IllegalArgumentException("Simulación no encontrada: " + simulacionId);
        }
        return state;
    }

    // ── Orquestación ──────────────────────────────────────────────────────

    private void ejecutarSimulacion(SimulacionState state, EscenarioSimulacion escenario,
                                    List<Integer> listaWorkers) {
        try {
            Thread.sleep(ESPERA_CONEXION_SSE_MS);

            int totalBloques = listaWorkers.size() * TecnicaSincronizacion.values().length;
            int bloque = 0;

            for (int workers : listaWorkers) {
                for (TecnicaSincronizacion tecnica : TecnicaSincronizacion.values()) {
                    bloque++;
                    state.emitirClave(EventoSimulacion.tecnicaIniciada(
                            tecnica, workers, bloque, totalBloques));

                    List<MetricasSimulacion> corridas =
                            ejecutarTecnica(state, tecnica, escenario, workers);

                    state.agregarResultados(corridas);
                    state.emitirClave(EventoSimulacion.tecnicaCompletada(
                            tecnica, workers, promediar(corridas)));

                    Thread.sleep(PAUSA_ENTRE_TECNICAS_MS);
                }
            }

            state.emitirClave(EventoSimulacion.simulacionCompleta(state.getResultados()));
            state.completar();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.fallar("Simulación interrumpida");
        } catch (Exception e) {
            state.fallar(e.getMessage());
        }
    }

    /**
     * Ejecuta las 5 repeticiones de una técnica para un nivel de workers dado.
     * Cada repetición arranca con butacas nuevas (reset completo a LIBRE).
     */
    private List<MetricasSimulacion> ejecutarTecnica(SimulacionState state,
                                                     TecnicaSincronizacion tecnica,
                                                     EscenarioSimulacion escenario,
                                                     int workers) throws InterruptedException {
        EstrategiaSincronizacion estrategia = crearEstrategia(tecnica);
        List<MetricasSimulacion> corridas = new ArrayList<>(REPETICIONES);

        // Warmup JVM: 50 solicitudes descartadas sobre butacas descartables
        int[] objetivosWarmup = generarObjetivos(SOLICITUDES_WARMUP, new Random(SEMILLA_BASE - 1));
        ejecutarCorrida(estrategia, crearButacas(), objetivosWarmup, workers, null);

        for (int rep = 1; rep <= REPETICIONES; rep++) {
            List<ButacaSimulada> butacas = crearButacas();

            // Misma semilla por (escenario, repetición) para las 3 técnicas
            long semilla = SEMILLA_BASE + escenario.ordinal() * 1000L + rep;
            int[] objetivos = generarObjetivos(escenario.getSolicitudes(), new Random(semilla));

            SimulacionState visual = (rep == 1) ? state : null;
            ResultadoCorrida r = ejecutarCorrida(estrategia, butacas, objetivos, workers, visual);

            corridas.add(construirMetricas(tecnica, escenario, workers, rep, r));
        }
        return corridas;
    }

    /**
     * Corrida única: W workers consumen las N solicitudes desde un índice
     * atómico compartido, todos arrancando en simultáneo.
     *
     * @param visual si no es null, se emiten eventos SSE por cada cambio de butaca
     */
    private ResultadoCorrida ejecutarCorrida(EstrategiaSincronizacion estrategia,
                                             List<ButacaSimulada> butacas,
                                             int[] objetivos,
                                             int workers,
                                             SimulacionState visual) throws InterruptedException {
        int n = objetivos.length;
        long[] latenciasNanos = new long[n];
        AtomicInteger indice = new AtomicInteger();
        AtomicInteger exitosas = new AtomicInteger();
        AtomicInteger fallidas = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();

        CountDownLatch arranque = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(workers);

        for (int w = 0; w < workers; w++) {
            Thread hilo = new Thread(() -> {
                try {
                    arranque.await();
                    int i;
                    while ((i = indice.getAndIncrement()) < n) {
                        ButacaSimulada butaca = butacas.get(objetivos[i]);

                        if (visual != null) {
                            visual.emitir(EventoSimulacion.butacaActualizada(
                                    butaca.getId(), estrategia.tecnica(), "EN_PROCESO"));
                        }

                        long t0 = System.nanoTime();
                        EstrategiaSincronizacion.ResultadoIntento res = estrategia.reservar(butaca);
                        latenciasNanos[i] = System.nanoTime() - t0;

                        if (res.exitosa()) exitosas.incrementAndGet();
                        else               fallidas.incrementAndGet();
                        conflictos.addAndGet(res.conflictos());

                        // Tras el intento la butaca siempre quedó OCUPADA
                        // (la ganó este hilo o ya la tenía otro)
                        if (visual != null) {
                            visual.emitir(EventoSimulacion.butacaActualizada(
                                    butaca.getId(), estrategia.tecnica(), "OCUPADA"));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            }, "sim-worker-" + w);
            hilo.setDaemon(true);
            hilo.start();
        }

        long inicio = System.nanoTime();
        arranque.countDown();
        fin.await();
        long wallNanos = System.nanoTime() - inicio;

        return new ResultadoCorrida(wallNanos, exitosas.get(), fallidas.get(),
                conflictos.get(), latenciasNanos);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private EstrategiaSincronizacion crearEstrategia(TecnicaSincronizacion tecnica) {
        return switch (tecnica) {
            case CAS_ATOMICO    -> new CasEstrategia();
            case REENTRANT_LOCK -> new LockEstrategia();
            case SYNCHRONIZED   -> new SynchronizedEstrategia();
        };
    }

    /** Crea las 120 butacas en LIBRE — réplica de la grilla de Sala Estrenos. */
    private List<ButacaSimulada> crearButacas() {
        List<ButacaSimulada> butacas = new ArrayList<>(FILAS * COLUMNAS);
        for (int f = 0; f < FILAS; f++) {
            for (int c = 0; c < COLUMNAS; c++) {
                String id = String.valueOf((char) ('A' + f)) + (c + 1);
                butacas.add(new ButacaSimulada(id, f, c));
            }
        }
        return butacas;
    }

    /** Índices de butaca objetivo (0..119) para cada solicitud. */
    private int[] generarObjetivos(int solicitudes, Random random) {
        int[] objetivos = new int[solicitudes];
        for (int i = 0; i < solicitudes; i++) {
            objetivos[i] = random.nextInt(FILAS * COLUMNAS);
        }
        return objetivos;
    }

    private MetricasSimulacion construirMetricas(TecnicaSincronizacion tecnica,
                                                 EscenarioSimulacion escenario,
                                                 int workers, int repeticion,
                                                 ResultadoCorrida r) {
        long sumaNanos = 0, maxNanos = Long.MIN_VALUE, minNanos = Long.MAX_VALUE;
        for (long lat : r.latenciasNanos()) {
            sumaNanos += lat;
            if (lat > maxNanos) maxNanos = lat;
            if (lat < minNanos) minNanos = lat;
        }
        int n = r.latenciasNanos().length;

        return new MetricasSimulacion(
                tecnica.name(),
                escenario.name(),
                workers,
                repeticion,
                r.wallNanos() / 1_000_000,
                n * 1_000_000_000.0 / r.wallNanos(),
                r.exitosas(),
                r.fallidas(),
                r.conflictos(),
                (sumaNanos / (double) n) / 1_000_000.0,
                maxNanos / 1_000_000,
                minNanos / 1_000_000
        );
    }

    /** Promedia las repeticiones de un bloque (para el evento TECNICA_COMPLETADA). */
    private MetricasSimulacion promediar(List<MetricasSimulacion> corridas) {
        int n = corridas.size();
        MetricasSimulacion ref = corridas.get(0);
        return new MetricasSimulacion(
                ref.tecnica(), ref.escenario(), ref.workers(),
                0,  // repetición 0 = promedio
                Math.round(corridas.stream().mapToLong(MetricasSimulacion::wallClockTimeMs).average().orElse(0)),
                corridas.stream().mapToDouble(MetricasSimulacion::throughput).average().orElse(0),
                (int) Math.round(corridas.stream().mapToInt(MetricasSimulacion::reservasExitosas).average().orElse(0)),
                (int) Math.round(corridas.stream().mapToInt(MetricasSimulacion::reservasFallidas).average().orElse(0)),
                (int) Math.round(corridas.stream().mapToInt(MetricasSimulacion::conflictosDetectados).average().orElse(0)),
                corridas.stream().mapToDouble(MetricasSimulacion::latenciaPromedioMs).average().orElse(0),
                Math.round(corridas.stream().mapToLong(MetricasSimulacion::latenciaMaxMs).average().orElse(0)),
                Math.round(corridas.stream().mapToLong(MetricasSimulacion::latenciaMinMs).average().orElse(0))
        );
    }

    private record ResultadoCorrida(long wallNanos, int exitosas, int fallidas,
                                    int conflictos, long[] latenciasNanos) {}

    @PreDestroy
    public void apagar() {
        orquestador.shutdownNow();
    }
}

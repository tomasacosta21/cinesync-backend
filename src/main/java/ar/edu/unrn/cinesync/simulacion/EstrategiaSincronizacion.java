package ar.edu.unrn.cinesync.simulacion;

/**
 * Estrategia intercambiable de sincronización para reservar una butaca simulada.
 *
 * Cada implementación protege la misma sección crítica (validar LIBRE →
 * trabajo de validación simulado → ocupar) con un mecanismo distinto,
 * de modo que las métricas reflejen solo el costo del mecanismo.
 */
public interface EstrategiaSincronizacion {

    /**
     * Tiempo de "trabajo de negocio" simulado dentro de la sección crítica
     * (~1 ms de validación). Sin esta carga, la operación sería tan corta que
     * el overhead de los hilos dominaría la medición y el speedup con más
     * workers no sería observable. Se usa busy-wait (no sleep) para que el
     * hilo retenga el CPU igual que una validación real.
     */
    long TRABAJO_NANOS = 1_000_000L;

    TecnicaSincronizacion tecnica();

    /**
     * Intenta reservar la butaca.
     *
     * @return resultado con éxito/fracaso y la cantidad de conflictos
     *         detectados durante el intento (reintentos de CAS o espera
     *         por un lock/monitor contendido).
     */
    ResultadoIntento reservar(ButacaSimulada butaca);

    record ResultadoIntento(boolean exitosa, int conflictos) {}

    static void trabajoSimulado() {
        long fin = System.nanoTime() + TRABAJO_NANOS;
        while (System.nanoTime() < fin) {
            Thread.onSpinWait();
        }
    }
}

package ar.edu.unrn.cinesync.simulacion;

/**
 * Métricas de una corrida individual de la simulación comparativa.
 *
 * @param tecnica             CAS_ATOMICO / REENTRANT_LOCK / SYNCHRONIZED
 * @param escenario           BAJA_CONTENCION / ALTA_CONTENCION
 * @param workers             cantidad de hilos trabajadores
 * @param repeticion          número de corrida (1..5)
 * @param wallClockTimeMs     System.nanoTime() inicio→fin, en ms
 * @param throughput          solicitudes procesadas por segundo
 * @param reservasExitosas    intentos que ganaron la butaca
 * @param reservasFallidas    intentos sobre butaca ya ocupada
 * @param conflictosDetectados CAS perdidos o locks/monitores contendidos
 * @param latenciaPromedioMs  tiempo promedio por solicitud
 * @param latenciaMaxMs       latencia máxima observada
 * @param latenciaMinMs       latencia mínima observada
 */
public record MetricasSimulacion(
        String tecnica,
        String escenario,
        int workers,
        int repeticion,
        long wallClockTimeMs,
        double throughput,
        int reservasExitosas,
        int reservasFallidas,
        int conflictosDetectados,
        double latenciaPromedioMs,
        long latenciaMaxMs,
        long latenciaMinMs
) {}

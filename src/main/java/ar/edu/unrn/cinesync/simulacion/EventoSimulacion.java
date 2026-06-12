package ar.edu.unrn.cinesync.simulacion;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Evento SSE de la simulación comparativa. Un único record cubre los cuatro
 * tipos; los campos que no aplican van en null y Jackson los omite del JSON.
 *
 * Tipos:
 *   TECNICA_INICIADA    → tecnica, workers, bloque, totalBloques
 *   BUTACA_ACTUALIZADA  → butacaId, tecnica, estado
 *   TECNICA_COMPLETADA  → tecnica, workers, metricas (promedio de las repeticiones)
 *   SIMULACION_COMPLETA → resultados (todas las corridas individuales)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventoSimulacion(
        String tipo,
        String butacaId,
        String tecnica,
        String estado,
        Integer workers,
        Integer bloque,
        Integer totalBloques,
        MetricasSimulacion metricas,
        List<MetricasSimulacion> resultados
) {

    public static EventoSimulacion tecnicaIniciada(TecnicaSincronizacion tecnica, int workers,
                                                   int bloque, int totalBloques) {
        return new EventoSimulacion("TECNICA_INICIADA", null, tecnica.name(), null,
                workers, bloque, totalBloques, null, null);
    }

    public static EventoSimulacion butacaActualizada(String butacaId,
                                                     TecnicaSincronizacion tecnica, String estado) {
        return new EventoSimulacion("BUTACA_ACTUALIZADA", butacaId, tecnica.name(), estado,
                null, null, null, null, null);
    }

    public static EventoSimulacion tecnicaCompletada(TecnicaSincronizacion tecnica, int workers,
                                                     MetricasSimulacion promedio) {
        return new EventoSimulacion("TECNICA_COMPLETADA", null, tecnica.name(), null,
                workers, null, null, promedio, null);
    }

    public static EventoSimulacion simulacionCompleta(List<MetricasSimulacion> resultados) {
        return new EventoSimulacion("SIMULACION_COMPLETA", null, null, null,
                null, null, null, null, resultados);
    }
}

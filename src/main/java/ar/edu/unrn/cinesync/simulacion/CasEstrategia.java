package ar.edu.unrn.cinesync.simulacion;

/**
 * Estrategia optimista basada en compareAndSet (mismo mecanismo que Butaca.reservar()).
 *
 * Patrón lectura-validación-CAS: se lee el estado, se hace el trabajo de
 * validación SIN ningún lock y recién al final se intenta el CAS. Si otro
 * hilo ocupó la butaca en el medio, el CAS falla → conflicto → reintento.
 * Bajo alta contención esto produce trabajo desperdiciado (la firma clásica
 * de los algoritmos lock-free optimistas).
 */
public class CasEstrategia implements EstrategiaSincronizacion {

    @Override
    public TecnicaSincronizacion tecnica() { return TecnicaSincronizacion.CAS_ATOMICO; }

    @Override
    public ResultadoIntento reservar(ButacaSimulada butaca) {
        int conflictos = 0;
        while (true) {
            if (butaca.getEstado() != ButacaSimulada.Estado.LIBRE) {
                // Butaca ya ocupada: reserva fallida
                return new ResultadoIntento(false, conflictos);
            }
            EstrategiaSincronizacion.trabajoSimulado();
            if (butaca.casOcupar()) {
                return new ResultadoIntento(true, conflictos);
            }
            // CAS perdido: otro hilo se adelantó durante la validación
            conflictos++;
        }
    }
}

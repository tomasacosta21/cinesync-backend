package ar.edu.unrn.cinesync.simulacion;

/**
 * Estrategia pesimista con monitor intrínseco: bloque synchronized sobre el
 * propio objeto ButacaSimulada.
 *
 * Detección de conflicto: el monitor de Java no expone una API tipo tryLock,
 * así que se usa una heurística temporal — si entrar al bloque tardó más de
 * {@link #UMBRAL_CONTENCION_NANOS} se asume que el monitor estaba contendido.
 * El umbral (50 µs) está muy por encima del costo de adquirir un monitor
 * libre (~decenas de ns) y muy por debajo del trabajo simulado (1 ms), por lo
 * que distingue ambos casos con margen amplio.
 */
public class SynchronizedEstrategia implements EstrategiaSincronizacion {

    static final long UMBRAL_CONTENCION_NANOS = 50_000L;

    @Override
    public TecnicaSincronizacion tecnica() { return TecnicaSincronizacion.SYNCHRONIZED; }

    @Override
    public ResultadoIntento reservar(ButacaSimulada butaca) {
        long antesDeEntrar = System.nanoTime();
        synchronized (butaca) {
            int conflictos =
                    (System.nanoTime() - antesDeEntrar) > UMBRAL_CONTENCION_NANOS ? 1 : 0;

            if (butaca.getEstado() != ButacaSimulada.Estado.LIBRE) {
                return new ResultadoIntento(false, conflictos);
            }
            EstrategiaSincronizacion.trabajoSimulado();
            butaca.setEstado(ButacaSimulada.Estado.OCUPADA);
            return new ResultadoIntento(true, conflictos);
        }
    }
}

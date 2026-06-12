package ar.edu.unrn.cinesync.simulacion;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Estrategia pesimista con ReentrantLock por butaca (mismo esquema que
 * ReservaServiceImpl, pero no-fair para igualar la política del monitor
 * synchronized y mantener la comparación homogénea).
 *
 * Detección de conflicto: tryLock() falla → el lock estaba contendido en el
 * momento del intento → se cuenta 1 conflicto y se espera con lock() normal.
 */
public class LockEstrategia implements EstrategiaSincronizacion {

    @Override
    public TecnicaSincronizacion tecnica() { return TecnicaSincronizacion.REENTRANT_LOCK; }

    @Override
    public ResultadoIntento reservar(ButacaSimulada butaca) {
        ReentrantLock lock = butaca.getLock();
        int conflictos = 0;

        if (!lock.tryLock()) {
            conflictos++;
            lock.lock();
        }
        try {
            if (butaca.getEstado() != ButacaSimulada.Estado.LIBRE) {
                return new ResultadoIntento(false, conflictos);
            }
            EstrategiaSincronizacion.trabajoSimulado();
            butaca.setEstado(ButacaSimulada.Estado.OCUPADA);
            return new ResultadoIntento(true, conflictos);
        } finally {
            lock.unlock();
        }
    }
}

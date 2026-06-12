package ar.edu.unrn.cinesync.simulacion;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Butaca exclusiva de la simulación comparativa.
 *
 * Es deliberadamente independiente de {@link ar.edu.unrn.cinesync.model.Butaca}
 * para no interferir con el flujo de reserva real de CineSync: cada corrida
 * crea instancias nuevas (reset completo) sin tocar las salas del servicio.
 *
 * Expone los tres mecanismos bajo comparación sobre el mismo dato:
 *   - AtomicReference  → estrategia CAS_ATOMICO (compareAndSet)
 *   - ReentrantLock    → estrategia REENTRANT_LOCK (get/set dentro del lock)
 *   - el propio objeto → estrategia SYNCHRONIZED (bloque synchronized sobre this)
 */
public class ButacaSimulada {

    public enum Estado { LIBRE, OCUPADA }

    private final String id;
    private final int fila;
    private final int columna;
    private final AtomicReference<Estado> estado = new AtomicReference<>(Estado.LIBRE);

    /** Lock no-fair: misma política de adquisición que un monitor synchronized,
     *  para que la comparación entre técnicas sea homogénea. */
    private final ReentrantLock lock = new ReentrantLock();

    public ButacaSimulada(String id, int fila, int columna) {
        this.id = id;
        this.fila = fila;
        this.columna = columna;
    }

    /** CAS atómico LIBRE → OCUPADA. @return true si este hilo ganó la carrera. */
    public boolean casOcupar() {
        return estado.compareAndSet(Estado.LIBRE, Estado.OCUPADA);
    }

    public Estado getEstado()        { return estado.get(); }
    public void setEstado(Estado e)  { estado.set(e); }
    public ReentrantLock getLock()   { return lock; }
    public String getId()            { return id; }
    public int getFila()             { return fila; }
    public int getColumna()          { return columna; }
}

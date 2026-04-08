package ar.edu.unrn.cinesync.model;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Representa una butaca individual dentro de una sala.
 *
 * --- CONCURRENCIA ---
 * El estado se gestiona con AtomicReference<EstadoButaca>, que usa internamente
 * instrucciones CAS (Compare-And-Swap) del procesador. Esto garantiza que
 * las transiciones de estado sean atómicas sin necesidad de synchronized.
 *
 * Complementariamente, ReservaServiceImpl agrega un ReentrantLock por butaca
 * para proteger la lógica de negocio completa (validación + cambio + notificación).
 */
public class Butaca {

    private final String id;       // "A1", "B5", "J12", etc.
    private final int fila;
    private final int columna;
    private final AtomicReference<EstadoButaca> estado;

    public Butaca(String id, int fila, int columna) {
        this.id = id;
        this.fila = fila;
        this.columna = columna;
        this.estado = new AtomicReference<>(EstadoButaca.LIBRE);
    }

    /**
     * CAS atómico: LIBRE → RESERVADA.
     * @return true si este hilo ganó la carrera; false si otro se adelantó.
     */
    public boolean reservar() {
        return estado.compareAndSet(EstadoButaca.LIBRE, EstadoButaca.RESERVADA);
    }

    /**
     * CAS atómico: RESERVADA → OCUPADA (pago confirmado).
     * @return true si el cambio fue exitoso.
     */
    public boolean confirmar() {
        return estado.compareAndSet(EstadoButaca.RESERVADA, EstadoButaca.OCUPADA);
    }

    /**
     * CAS atómico: RESERVADA → LIBRE (cancelación o timeout).
     * @return true si el cambio fue exitoso.
     */
    public boolean liberar() {
        return estado.compareAndSet(EstadoButaca.RESERVADA, EstadoButaca.LIBRE);
    }

    public EstadoButaca getEstado() { return estado.get(); }
    public String getId()           { return id; }
    public int getFila()            { return fila; }
    public int getColumna()         { return columna; }
}

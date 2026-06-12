package ar.edu.unrn.cinesync.simulacion;

/**
 * Técnicas de sincronización comparadas por la simulación.
 * El orden de declaración define el orden de ejecución secuencial.
 */
public enum TecnicaSincronizacion {
    CAS_ATOMICO,
    REENTRANT_LOCK,
    SYNCHRONIZED
}

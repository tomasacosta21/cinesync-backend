package ar.edu.unrn.cinesync.model;

/**
 * Estados posibles de una butaca en la sala.
 *
 * Transiciones válidas:
 *   LIBRE → RESERVADA  (usuario selecciona y confirma)
 *   RESERVADA → OCUPADA  (pago confirmado)
 *   RESERVADA → LIBRE    (timeout o cancelación)
 */
public enum EstadoButaca {
    LIBRE,
    RESERVADA,
    OCUPADA
}

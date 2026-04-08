package ar.edu.unrn.cinesync.model;

import java.time.Instant;

/**
 * Mensaje que viaja por la LinkedBlockingQueue (patrón Productor-Consumidor).
 * Es inmutable por ser un record de Java 21.
 */
public record SolicitudReserva(
        String usuarioId,
        int    salaId,
        String butacaId,
        Instant timestamp
) {}

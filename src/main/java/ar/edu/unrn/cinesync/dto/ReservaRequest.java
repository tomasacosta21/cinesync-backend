package ar.edu.siglo21.cinesync.dto;

/** Body del POST /api/salas/{id}/reservar */
public record ReservaRequest(String usuarioId, String butacaId) {}

package ar.edu.siglo21.cinesync.dto;

/** Respuesta de las operaciones de reserva. */
public record ReservaResponse(boolean exitoso, String mensaje, String butacaId, String estado) {}

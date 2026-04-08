package ar.edu.unrn.cinesync.dto;

/**
 * Payload que viaja por Server-Sent Events hacia el frontend Angular.
 * Cuando una butaca cambia de estado, todos los clientes conectados
 * reciben este objeto serializado como JSON.
 */
public record SseEventDTO(int salaId, String butacaId, String estado) {}

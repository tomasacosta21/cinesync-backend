package ar.edu.siglo21.cinesync.dto;

/** Estado de una butaca serializable a JSON. */
public record ButacaDTO(String id, int fila, int columna, String estado) {}

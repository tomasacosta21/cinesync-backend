package ar.edu.unrn.cinesync.dto;

/** Estado de una butaca serializable a JSON. */
public record ButacaDTO(String id, int fila, int columna, String estado) {}

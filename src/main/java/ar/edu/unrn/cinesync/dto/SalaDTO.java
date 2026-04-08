package ar.edu.unrn.cinesync.dto;

import java.util.List;

/** Representación de una sala completa para la API REST. */
public record SalaDTO(int id, String nombre, int filas, int columnas, List<ButacaDTO> butacas) {}

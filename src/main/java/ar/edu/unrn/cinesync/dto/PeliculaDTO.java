package ar.edu.unrn.cinesync.dto;

/** DTO de película para la API REST. */
public record PeliculaDTO(
        String id,
        String titulo,
        String director,
        String genero,
        int duracionMinutos,
        String sinopsis,
        String imagen,
        String clasificacion
) {}

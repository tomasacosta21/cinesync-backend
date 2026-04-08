package ar.edu.unrn.cinesync.dto;

/** Body del POST/PUT para crear o editar una película. */
public record PeliculaRequest(
        String titulo,
        String director,
        String genero,
        int duracionMinutos,
        String sinopsis,
        String imagen,
        String clasificacion
) {}

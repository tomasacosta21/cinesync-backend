package ar.edu.unrn.cinesync.dto;

import java.time.LocalDateTime;

/** DTO de proyección enriquecido con datos de la película. */
public record ProyeccionDTO(
        String id,
        int salaId,
        String salaNombre,
        String peliculaId,
        String peliculaTitulo,
        String peliculaImagen,
        String peliculaClasificacion,
        int peliculaDuracionMinutos,
        LocalDateTime fechaHora,
        double precioBase,
        String estado
) {}

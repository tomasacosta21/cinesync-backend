package ar.edu.unrn.cinesync.dto;

import java.time.LocalDateTime;

/** Body del POST/PUT para crear o editar una proyección. */
public record ProyeccionRequest(
        int salaId,
        String peliculaId,
        LocalDateTime fechaHora,
        double precioBase
) {}

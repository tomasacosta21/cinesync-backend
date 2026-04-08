package ar.edu.unrn.cinesync.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa una función de una película en una sala en una fecha/hora específica.
 *
 * Las butacas pertenecen a la Sala y se resetean cuando se cambia la proyección
 * activa (modelo simplificado elegido para este TP).
 *
 * Una proyección puede estar en uno de estos estados:
 *   PROGRAMADA → todavía no comenzó
 *   EN_CURSO   → está pasando ahora
 *   FINALIZADA → ya terminó
 *   CANCELADA  → fue cancelada
 */
public class Proyeccion {

    public enum Estado { PROGRAMADA, EN_CURSO, FINALIZADA, CANCELADA }

    private final String id;
    private final int salaId;
    private String peliculaId;
    private LocalDateTime fechaHora;
    private double precioBase;
    private Estado estado;

    public Proyeccion(int salaId, String peliculaId,
                      LocalDateTime fechaHora, double precioBase) {
        this.id          = UUID.randomUUID().toString();
        this.salaId      = salaId;
        this.peliculaId  = peliculaId;
        this.fechaHora   = fechaHora;
        this.precioBase  = precioBase;
        this.estado      = Estado.PROGRAMADA;
    }

    public String getId()              { return id; }
    public int getSalaId()             { return salaId; }
    public String getPeliculaId()      { return peliculaId; }
    public LocalDateTime getFechaHora(){ return fechaHora; }
    public double getPrecioBase()      { return precioBase; }
    public Estado getEstado()          { return estado; }

    public void setPeliculaId(String peliculaId)    { this.peliculaId = peliculaId; }
    public void setFechaHora(LocalDateTime fechaHora){ this.fechaHora = fechaHora; }
    public void setPrecioBase(double precioBase)     { this.precioBase = precioBase; }
    public void setEstado(Estado estado)             { this.estado = estado; }
}

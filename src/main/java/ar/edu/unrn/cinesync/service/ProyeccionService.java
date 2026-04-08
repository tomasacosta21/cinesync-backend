package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.model.Proyeccion;
import java.time.LocalDateTime;
import java.util.List;

public interface ProyeccionService {
    List<Proyeccion> listarPorSala(int salaId);
    List<Proyeccion> listarTodas();
    Proyeccion obtener(String id);
    Proyeccion crear(int salaId, String peliculaId, LocalDateTime fechaHora, double precioBase);
    Proyeccion actualizar(String id, String peliculaId, LocalDateTime fechaHora, double precioBase);
    void cancelar(String id);
    void eliminar(String id);

    /**
     * Activa una proyección en su sala: resetea las butacas y marca
     * la proyección como la activa de esa sala.
     */
    void activar(String proyeccionId);
}

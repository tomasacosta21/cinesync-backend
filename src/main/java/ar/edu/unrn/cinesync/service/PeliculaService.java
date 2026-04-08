package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.model.Pelicula;
import java.util.List;

public interface PeliculaService {
    List<Pelicula> listar();
    Pelicula obtener(String id);
    Pelicula crear(String titulo, String director, String genero,
                   int duracionMinutos, String sinopsis,
                   String imagen, String clasificacion);
    Pelicula actualizar(String id, String titulo, String director, String genero,
                        int duracionMinutos, String sinopsis,
                        String imagen, String clasificacion);
    void eliminar(String id);
}

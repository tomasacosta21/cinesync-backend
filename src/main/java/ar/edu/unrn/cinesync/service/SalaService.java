package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.model.Sala;

import java.util.List;

public interface SalaService {
    List<Sala> listarSalas();
    Sala obtenerSala(int salaId);
}

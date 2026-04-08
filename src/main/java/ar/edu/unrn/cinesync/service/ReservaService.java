package ar.edu.unrn.cinesync.service;

public interface ReservaService {
    boolean reservar(int salaId, String butacaId, String usuarioId);
    boolean confirmar(int salaId, String butacaId);
    boolean liberar(int salaId, String butacaId);
}

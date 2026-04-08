package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.model.Sala;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra las 3 salas del cine.
 * Las salas se inicializan una sola vez al arrancar Spring y son inmutables
 * en estructura — solo cambia el estado interno de cada butaca.
 *
 * ConcurrentHashMap garantiza lecturas thread-safe sin locks explícitos.
 */
@Service
public class SalaServiceImpl implements SalaService {

    private final Map<Integer, Sala> salas = new ConcurrentHashMap<>();

    public SalaServiceImpl() {
        salas.put(1, new Sala(1, "Sala Estrenos", 10, 12));  // 120 butacas
        salas.put(2, new Sala(2, "Sala Nueva",    8,  10));  //  80 butacas
        salas.put(3, new Sala(3, "Sala Clásica",  5,   8));  //  40 butacas
    }

    @Override
    public List<Sala> listarSalas() {
        return List.copyOf(salas.values());
    }

    @Override
    public Sala obtenerSala(int salaId) {
        Sala sala = salas.get(salaId);
        if (sala == null) {
            throw new IllegalArgumentException("Sala no encontrada: " + salaId);
        }
        return sala;
    }
}

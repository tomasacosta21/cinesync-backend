package ar.edu.unrn.cinesync.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa una sala de cine con su grilla de butacas.
 *
 * Las butacas se identifican como "A1"…"A12", "B1"…"B12", etc.
 * La sala no es mutable en estructura después de su construcción;
 * solo cambia el estado interno de cada Butaca (thread-safe via AtomicReference).
 *
 * Cuando cambia la proyección activa, resetearButacas() vuelve
 * todas las butacas a LIBRE para la nueva función.
 */
public class Sala {

    private final int id;
    private final String nombre;
    private final int filas;
    private final int columnas;
    private final List<Butaca> butacas;

    /** ID de la proyección actualmente seleccionada para esta sala (puede ser null). */
    private volatile String proyeccionActivaId;

    public Sala(int id, String nombre, int filas, int columnas) {
        this.id       = id;
        this.nombre   = nombre;
        this.filas    = filas;
        this.columnas = columnas;
        this.butacas  = new ArrayList<>(filas * columnas);
        inicializar();
    }

    private void inicializar() {
        for (int f = 0; f < filas; f++) {
            for (int c = 0; c < columnas; c++) {
                String butacaId = String.valueOf((char) ('A' + f)) + (c + 1);
                butacas.add(new Butaca(butacaId, f, c));
            }
        }
    }

    /**
     * Resetea todas las butacas a LIBRE y registra la proyección activa.
     * Se llama cuando el usuario selecciona una proyección distinta para esta sala.
     *
     * Crea nuevas instancias de Butaca en lugar de mutar las existentes,
     * lo que garantiza un estado limpio sin race conditions con hilos que
     * pudieran estar leyendo el estado anterior.
     */
    public synchronized void activarProyeccion(String proyeccionId) {
        this.proyeccionActivaId = proyeccionId;
        butacas.clear();
        inicializar();
    }

    /**
     * Busca una butaca por su ID.
     * @throws IllegalArgumentException si el ID no existe en esta sala.
     */
    public Butaca buscarButaca(String butacaId) {
        return butacas.stream()
                .filter(b -> b.getId().equals(butacaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Butaca '%s' no existe en sala %d".formatted(butacaId, id)));
    }

    public List<Butaca> getButacas()         { return Collections.unmodifiableList(butacas); }
    public int getId()                       { return id; }
    public String getNombre()                { return nombre; }
    public int getFilas()                    { return filas; }
    public int getColumnas()                 { return columnas; }
    public String getProyeccionActivaId()    { return proyeccionActivaId; }
}

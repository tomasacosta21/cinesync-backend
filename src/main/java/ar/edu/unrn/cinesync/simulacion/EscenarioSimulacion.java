package ar.edu.unrn.cinesync.simulacion;

/**
 * Escenarios de carga sobre las 120 butacas de Sala Estrenos.
 *
 *   BAJA_CONTENCION: 120 solicitudes / 120 butacas (ratio 1:1)
 *   ALTA_CONTENCION: 500 solicitudes / 120 butacas (ratio ~4:1)
 */
public enum EscenarioSimulacion {
    BAJA_CONTENCION(120),
    ALTA_CONTENCION(500);

    private final int solicitudes;

    EscenarioSimulacion(int solicitudes) {
        this.solicitudes = solicitudes;
    }

    public int getSolicitudes() { return solicitudes; }
}

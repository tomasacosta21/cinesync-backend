package ar.edu.unrn.cinesync.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Sala — inicialización y estructura")
class SalaTest {

    @Test
    @DisplayName("Sala Estrenos debe tener 120 butacas (10×12)")
    void salaEstrenosTiene120Butacas() {
        Sala sala = new Sala(1, "Sala Estrenos", 10, 12);
        assertThat(sala.getButacas()).hasSize(120);
    }

    @Test
    @DisplayName("Sala Nueva debe tener 80 butacas (8×10)")
    void salaNuevaTiene80Butacas() {
        Sala sala = new Sala(2, "Sala Nueva", 8, 10);
        assertThat(sala.getButacas()).hasSize(80);
    }

    @Test
    @DisplayName("Sala Clásica debe tener 40 butacas (5×8)")
    void salaClasicaTiene40Butacas() {
        Sala sala = new Sala(3, "Sala Clásica", 5, 8);
        assertThat(sala.getButacas()).hasSize(40);
    }

    @Test
    @DisplayName("Todas las butacas deben iniciar en estado LIBRE")
    void todasLasButacasInicianLibres() {
        Sala sala = new Sala(1, "Sala Estrenos", 10, 12);
        assertThat(sala.getButacas())
                .extracting(Butaca::getEstado)
                .containsOnly(EstadoButaca.LIBRE);
    }

    @Test
    @DisplayName("IDs siguen el formato correcto: A1, A12, B1, J12")
    void formatoDeIdsEsCorrecto() {
        Sala sala = new Sala(1, "Sala Estrenos", 10, 12);
        assertThat(sala.getButacas().get(0).getId()).isEqualTo("A1");
        assertThat(sala.getButacas().get(11).getId()).isEqualTo("A12");
        assertThat(sala.getButacas().get(12).getId()).isEqualTo("B1");
        assertThat(sala.getButacas().get(119).getId()).isEqualTo("J12");
    }

    @Test
    @DisplayName("buscarButaca encuentra la butaca correcta por ID")
    void buscarButacaPorId() {
        Sala sala   = new Sala(1, "Sala Estrenos", 10, 12);
        Butaca b    = sala.buscarButaca("C5");
        assertThat(b.getId()).isEqualTo("C5");
        assertThat(b.getFila()).isEqualTo(2);       // C = índice 2
        assertThat(b.getColumna()).isEqualTo(4);    // columna 5 = índice 4
    }

    @Test
    @DisplayName("buscarButaca con ID inválido lanza IllegalArgumentException")
    void buscarButacaIdInvalidoLanzaExcepcion() {
        Sala sala = new Sala(1, "Sala Estrenos", 10, 12);
        assertThatThrownBy(() -> sala.buscarButaca("Z99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Z99");
    }
}

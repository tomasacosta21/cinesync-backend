package ar.edu.unrn.cinesync.service;

import ar.edu.unrn.cinesync.model.Pelicula;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona el catálogo de películas en memoria.
 * ConcurrentHashMap garantiza acceso thread-safe sin locks explícitos.
 * Se inicializa con películas de ejemplo para que el sistema tenga datos al arrancar.
 */
@Service
public class PeliculaServiceImpl implements PeliculaService {

    private final Map<String, Pelicula> peliculas = new ConcurrentHashMap<>();

    public PeliculaServiceImpl() {
        seedData();
    }

    private void seedData() {
        agregar(new Pelicula(
                "Dune: Parte Dos",
                "Denis Villeneuve",
                "Ciencia Ficción",
                166,
                "Paul Atreides continúa su viaje por Arrakis, uniéndose a los Fremen mientras busca venganza contra los conspiradores que destruyeron a su familia.",
                "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                "+13"
        ));
        agregar(new Pelicula(
                "Oppenheimer",
                "Christopher Nolan",
                "Drama / Historia",
                180,
                "La historia del físico J. Robert Oppenheimer y su papel en el desarrollo de la bomba atómica durante la Segunda Guerra Mundial.",
                "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                "+13"
        ));
        agregar(new Pelicula(
                "Poor Things",
                "Yorgos Lanthimos",
                "Drama / Fantasía",
                141,
                "La increíble historia de Bella Baxter, una joven traída de vuelta a la vida por el excéntrico y progresista científico Dr. Godwin Baxter.",
                "https://image.tmdb.org/t/p/w500/kCGlIMHnOm8JPXIRFug4Kt1aQhV.jpg",
                "+18"
        ));
        agregar(new Pelicula(
                "El Señor de los Anillos: La Comunidad del Anillo",
                "Peter Jackson",
                "Fantasía / Aventura",
                178,
                "Un hobbit llamado Frodo Bolsón y ocho compañeros emprenden un viaje para destruir el Anillo Único y derrotar al Señor Oscuro Sauron.",
                "https://image.tmdb.org/t/p/w500/6oom5QYQ2yQTMJIbnvbkBL9cHo6.jpg",
                "ATP"
        ));
        agregar(new Pelicula(
                "2001: Odisea del Espacio",
                "Stanley Kubrick",
                "Ciencia Ficción / Clásico",
                149,
                "La humanidad encuentra un misterioso monolito que parece influenciar la evolución humana. Una nave espacial es enviada a Júpiter con la IA HAL 9000.",
                "https://image.tmdb.org/t/p/w500/ve72VxNqjGM69Uky4WTo2bK6rfq.jpg",
                "ATP"
        ));
        agregar(new Pelicula(
                "Alien: Romulus",
                "Fede Álvarez",
                "Terror / Ciencia Ficción",
                119,
                "Un grupo de jóvenes colonos del espacio se enfrentan a la forma de vida más aterradora del universo mientras desguazan una estación espacial abandonada.",
                "https://image.tmdb.org/t/p/w500/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg",
                "+16"
        ));
        agregar(new Pelicula(
                "Anora",
                "Sean Baker",
                "Drama / Romance",
                139,
                "Una joven trabajadora sexual de Nueva York se casa impulsivamente con el hijo de un oligarca ruso, desencadenando una serie de eventos inesperados.",
                "https://image.tmdb.org/t/p/w500/4F9vgRn4tuvqBmxBgNpJENSA2JU.jpg",
                "+18"
        ));
    }

    private void agregar(Pelicula p) {
        peliculas.put(p.getId(), p);
    }

    @Override
    public List<Pelicula> listar() {
        return List.copyOf(peliculas.values());
    }

    @Override
    public Pelicula obtener(String id) {
        Pelicula p = peliculas.get(id);
        if (p == null) throw new IllegalArgumentException("Película no encontrada: " + id);
        return p;
    }

    @Override
    public Pelicula crear(String titulo, String director, String genero,
                          int duracionMinutos, String sinopsis,
                          String imagen, String clasificacion) {
        Pelicula p = new Pelicula(titulo, director, genero,
                duracionMinutos, sinopsis, imagen, clasificacion);
        peliculas.put(p.getId(), p);
        return p;
    }

    @Override
    public Pelicula actualizar(String id, String titulo, String director, String genero,
                               int duracionMinutos, String sinopsis,
                               String imagen, String clasificacion) {
        Pelicula p = obtener(id);
        p.setTitulo(titulo);
        p.setDirector(director);
        p.setGenero(genero);
        p.setDuracionMinutos(duracionMinutos);
        p.setSinopsis(sinopsis);
        p.setImagen(imagen);
        p.setClasificacion(clasificacion);
        return p;
    }

    @Override
    public void eliminar(String id) {
        if (peliculas.remove(id) == null) {
            throw new IllegalArgumentException("Película no encontrada: " + id);
        }
    }
}

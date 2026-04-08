package ar.edu.unrn.cinesync.controller;

import ar.edu.unrn.cinesync.dto.PeliculaDTO;
import ar.edu.unrn.cinesync.dto.PeliculaRequest;
import ar.edu.unrn.cinesync.model.Pelicula;
import ar.edu.unrn.cinesync.service.PeliculaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ABM de películas.
 *
 * GET    /api/peliculas         → lista todas
 * GET    /api/peliculas/{id}    → detalle
 * POST   /api/peliculas         → crear
 * PUT    /api/peliculas/{id}    → actualizar
 * DELETE /api/peliculas/{id}    → eliminar
 */
@RestController
@RequestMapping("/api/peliculas")
public class PeliculaController {

    private final PeliculaService peliculaService;

    public PeliculaController(PeliculaService peliculaService) {
        this.peliculaService = peliculaService;
    }

    @GetMapping
    public List<PeliculaDTO> listar() {
        return peliculaService.listar().stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    public PeliculaDTO obtener(@PathVariable String id) {
        return toDTO(peliculaService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<PeliculaDTO> crear(@RequestBody PeliculaRequest req) {
        Pelicula p = peliculaService.crear(
                req.titulo(), req.director(), req.genero(),
                req.duracionMinutos(), req.sinopsis(),
                req.imagen(), req.clasificacion()
        );
        return ResponseEntity.status(201).body(toDTO(p));
    }

    @PutMapping("/{id}")
    public PeliculaDTO actualizar(@PathVariable String id,
                                  @RequestBody PeliculaRequest req) {
        return toDTO(peliculaService.actualizar(
                id, req.titulo(), req.director(), req.genero(),
                req.duracionMinutos(), req.sinopsis(),
                req.imagen(), req.clasificacion()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        peliculaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private PeliculaDTO toDTO(Pelicula p) {
        return new PeliculaDTO(
                p.getId(), p.getTitulo(), p.getDirector(), p.getGenero(),
                p.getDuracionMinutos(), p.getSinopsis(),
                p.getImagen(), p.getClasificacion()
        );
    }
}

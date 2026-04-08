package ar.edu.unrn.cinesync.model;

import java.util.UUID;

/**
 * Representa una película del catálogo del cine.
 * Es inmutable después de su creación — el ID se genera automáticamente.
 */
public class Pelicula {

    private final String id;
    private String titulo;
    private String director;
    private String genero;
    private int duracionMinutos;
    private String sinopsis;
    private String imagen;          // URL de poster (opcional)
    private String clasificacion;   // ATP, +13, +16, +18

    public Pelicula(String titulo, String director, String genero,
                    int duracionMinutos, String sinopsis,
                    String imagen, String clasificacion) {
        this.id               = UUID.randomUUID().toString();
        this.titulo           = titulo;
        this.director         = director;
        this.genero           = genero;
        this.duracionMinutos  = duracionMinutos;
        this.sinopsis         = sinopsis;
        this.imagen           = imagen;
        this.clasificacion    = clasificacion;
    }

    public String getId()              { return id; }
    public String getTitulo()          { return titulo; }
    public String getDirector()        { return director; }
    public String getGenero()          { return genero; }
    public int getDuracionMinutos()    { return duracionMinutos; }
    public String getSinopsis()        { return sinopsis; }
    public String getImagen()          { return imagen; }
    public String getClasificacion()   { return clasificacion; }

    public void setTitulo(String titulo)                   { this.titulo = titulo; }
    public void setDirector(String director)               { this.director = director; }
    public void setGenero(String genero)                   { this.genero = genero; }
    public void setDuracionMinutos(int duracionMinutos)    { this.duracionMinutos = duracionMinutos; }
    public void setSinopsis(String sinopsis)               { this.sinopsis = sinopsis; }
    public void setImagen(String imagen)                   { this.imagen = imagen; }
    public void setClasificacion(String clasificacion)     { this.clasificacion = clasificacion; }
}

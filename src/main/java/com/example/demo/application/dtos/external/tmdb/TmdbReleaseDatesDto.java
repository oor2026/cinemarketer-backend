package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de TMDb a GET /movie/{id}/release_dates: las fechas de estreno de una película, país por país.
 * Cada fecha tiene un tipo (1 Premiere, 2 cine limitado, 3 cine, 4 digital, 5 físico, 6 TV) y una nota de texto libre.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbReleaseDatesDto {

    private Long id;
    private List<Pais> results;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<Pais> getResults() { return results; }
    public void setResults(List<Pais> results) { this.results = results; }

    /**
     * Arma el DTO a partir de lo que devuelve TmdbService.getReleaseDates: el JSON de TMDb ya leído como mapas y listas
     * (ese método devuelve Object y ya lo usa Creator Tools, con su caché). Lo que no entiende o viene mal formado se
     * ignora: nunca lanza una excepción por un dato raro.
     */
    public static TmdbReleaseDatesDto desde(Object crudo) {
        TmdbReleaseDatesDto dto = new TmdbReleaseDatesDto();
        dto.setResults(new ArrayList<>());
        if (!(crudo instanceof Map<?, ?> raiz)) return dto;
        if (raiz.get("id") instanceof Number n) dto.setId(n.longValue());
        if (!(raiz.get("results") instanceof List<?> paises)) return dto;

        for (Object p : paises) {
            if (!(p instanceof Map<?, ?> mapaPais)) continue;
            Pais pais = new Pais();
            pais.setCodigoPais(mapaPais.get("iso_3166_1") instanceof String s ? s : null);
            List<Fecha> fechas = new ArrayList<>();
            if (mapaPais.get("release_dates") instanceof List<?> lista) {
                for (Object f : lista) {
                    if (!(f instanceof Map<?, ?> mapaFecha)) continue;
                    Fecha fecha = new Fecha();
                    fecha.setType(mapaFecha.get("type") instanceof Number t ? t.intValue() : null);
                    fecha.setNote(mapaFecha.get("note") instanceof String s ? s : null);
                    fecha.setFecha(mapaFecha.get("release_date") instanceof String s ? s : null);
                    fechas.add(fecha);
                }
            }
            pais.setFechas(fechas);
            dto.getResults().add(pais);
        }
        return dto;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pais {
        @JsonProperty("iso_3166_1")
        private String codigoPais;

        @JsonProperty("release_dates")
        private List<Fecha> fechas;

        public String getCodigoPais() { return codigoPais; }
        public void setCodigoPais(String codigoPais) { this.codigoPais = codigoPais; }
        public List<Fecha> getFechas() { return fechas; }
        public void setFechas(List<Fecha> fechas) { this.fechas = fechas; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fecha {
        private Integer type;
        private String note;

        /** Llega como "2026-10-17T00:00:00.000Z": ver ReestrenoDetector, que toma solo la fecha. */
        @JsonProperty("release_date")
        private String fecha;

        public Integer getType() { return type; }
        public void setType(Integer type) { this.type = type; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getFecha() { return fecha; }
        public void setFecha(String fecha) { this.fecha = fecha; }
    }
}
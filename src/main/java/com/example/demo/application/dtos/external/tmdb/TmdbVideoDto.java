package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO para la respuesta de videos de TMDB
 * Endpoint: /movie/{id}/videos
 */
public class TmdbVideoDto {

    private Long id;
    private List<VideoResult> results;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<VideoResult> getResults() {
        return results;
    }

    public void setResults(List<VideoResult> results) {
        this.results = results;
    }

    /**
     * Clase interna para cada video individual
     */
    public static class VideoResult {

        @JsonProperty("iso_639_1")
        private String iso6391;

        @JsonProperty("iso_3166_1")
        private String iso31661;

        private String name;
        private String key;
        private String site;
        private Integer size;
        private String type;
        private Boolean official;

        @JsonProperty("published_at")
        private String publishedAt;

        private String id;

        // Getters y Setters
        public String getIso6391() {
            return iso6391;
        }

        public void setIso6391(String iso6391) {
            this.iso6391 = iso6391;
        }

        public String getIso31661() {
            return iso31661;
        }

        public void setIso31661(String iso31661) {
            this.iso31661 = iso31661;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getSite() {
            return site;
        }

        public void setSite(String site) {
            this.site = site;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Boolean getOfficial() {
            return official;
        }

        public void setOfficial(Boolean official) {
            this.official = official;
        }

        public String getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
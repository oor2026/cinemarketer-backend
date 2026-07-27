package com.example.demo.domain.feed;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feed_destacada")
public class FeedDestacado {

    @Id
    private Long id = 1L; // singleton — siempre el mismo registro, no autogenerado

    private Long movieId;

    private Long updatedByAdminId;
    private String updatedByAdminEmail;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }
    public Long getUpdatedByAdminId() { return updatedByAdminId; }
    public void setUpdatedByAdminId(Long updatedByAdminId) { this.updatedByAdminId = updatedByAdminId; }
    public String getUpdatedByAdminEmail() { return updatedByAdminEmail; }
    public void setUpdatedByAdminEmail(String updatedByAdminEmail) { this.updatedByAdminEmail = updatedByAdminEmail; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
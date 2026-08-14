package com.example.demo.domain.feed;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "series_feed_destacada")
public class SeriesFeedDestacado {

    @Id
    private Long id = 1L; // singleton — siempre el mismo registro

    private Long seriesId;

    private Long updatedByAdminId;
    private String updatedByAdminEmail;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Long getUpdatedByAdminId() { return updatedByAdminId; }
    public void setUpdatedByAdminId(Long updatedByAdminId) { this.updatedByAdminId = updatedByAdminId; }
    public String getUpdatedByAdminEmail() { return updatedByAdminEmail; }
    public void setUpdatedByAdminEmail(String updatedByAdminEmail) { this.updatedByAdminEmail = updatedByAdminEmail; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
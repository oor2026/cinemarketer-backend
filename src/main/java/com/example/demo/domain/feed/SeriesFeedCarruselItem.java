package com.example.demo.domain.feed;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "series_feed_carrusel_items")
public class SeriesFeedCarruselItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SeriesFeedCarruselTipo tipo;

    @Column(nullable = false)
    private Integer orderIndex;

    // Solo aplica para PREMIO_COMUN / PREMIO_ESPECIAL — null para el resto
    private Long rewardId;

    // Solo aplica para SERIE_CARRUSEL — null para el resto
    private Long seriesId;

    private Long updatedByAdminId;
    private String updatedByAdminEmail;
    private LocalDateTime addedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SeriesFeedCarruselTipo getTipo() { return tipo; }
    public void setTipo(SeriesFeedCarruselTipo tipo) { this.tipo = tipo; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public Long getRewardId() { return rewardId; }
    public void setRewardId(Long rewardId) { this.rewardId = rewardId; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Long getUpdatedByAdminId() { return updatedByAdminId; }
    public void setUpdatedByAdminId(Long updatedByAdminId) { this.updatedByAdminId = updatedByAdminId; }
    public String getUpdatedByAdminEmail() { return updatedByAdminEmail; }
    public void setUpdatedByAdminEmail(String updatedByAdminEmail) { this.updatedByAdminEmail = updatedByAdminEmail; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}

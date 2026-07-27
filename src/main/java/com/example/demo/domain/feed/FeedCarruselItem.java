package com.example.demo.domain.feed;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feed_carrusel_items")
public class FeedCarruselItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeedCarruselTipo tipo;

    @Column(nullable = false)
    private Integer orderIndex;

    // Solo aplica para PREMIO_COMUN / PREMIO_ESPECIAL — null para el resto
    private Long rewardId;

    // Solo aplica para PELICULA_CARRUSEL — null para el resto
    private Long movieId;

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }

    private Long updatedByAdminId;
    private String updatedByAdminEmail;
    private LocalDateTime addedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FeedCarruselTipo getTipo() { return tipo; }
    public void setTipo(FeedCarruselTipo tipo) { this.tipo = tipo; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public Long getRewardId() { return rewardId; }
    public void setRewardId(Long rewardId) { this.rewardId = rewardId; }
    public Long getUpdatedByAdminId() { return updatedByAdminId; }
    public void setUpdatedByAdminId(Long updatedByAdminId) { this.updatedByAdminId = updatedByAdminId; }
    public String getUpdatedByAdminEmail() { return updatedByAdminEmail; }
    public void setUpdatedByAdminEmail(String updatedByAdminEmail) { this.updatedByAdminEmail = updatedByAdminEmail; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
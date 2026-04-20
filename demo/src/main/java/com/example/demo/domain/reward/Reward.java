package com.example.demo.domain.reward;

import com.example.demo.domain.redemption.Redemption;
import com.example.demo.domain.sweepstake.Sweepstake;  // 👈 IMPORTAR
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rewards")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 20)
    private RewardType rewardType;  // TICKET o MERCHANDISING

    @Column(name = "points_required", nullable = false)
    private Integer pointsRequired;  // Puntos necesarios para canjear

    @Column(nullable = false)
    private Integer stock;  // Cantidad disponible (0 = agotado)

    @Column(name = "initial_stock", nullable = false)
    private Integer initialStock;  // Stock inicial (para referencia)

    @Column(name = "expiry_date")
    private LocalDate expiryDate;  // Fecha de expiración del premio

    @Column(name = "image_url", length = 500)
    private String imageUrl;  // Imagen del premio

    @Column(nullable = false)
    private Boolean active = true;  // visible para canje

    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;  // Términos específicos del premio

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(length = 255)
    private String partner;  // Socio/marca que brinda el premio

    @Column(length = 500)
    private String website;  // Sitio web o red social del socio

    // Relación con Redemption (ya existente)
    @OneToMany(mappedBy = "reward")
    private List<Redemption> redemptions = new ArrayList<>();

    // 👇 NUEVA RELACIÓN CON SWEEPSTAKE (un premio puede estar en varios sorteos)
    @OneToMany(mappedBy = "reward")
    private List<Sweepstake> sweepstakes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (initialStock == null) {
            initialStock = stock;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Métodos útiles
    public boolean hasStock() {
        return stock > 0;
    }

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    public boolean isAvailable() {
        return active && hasStock() && !isExpired();
    }

    public void decreaseStock() {
        if (stock > 0) {
            stock--;
        }
    }

    public void increaseStock() {
        stock++;
    }

    public void restoreStock() {
        this.stock = initialStock;
    }
}
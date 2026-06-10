package com.example.demo.domain.premium;

import com.example.demo.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "premium_rewards")
@NoArgsConstructor
@AllArgsConstructor
public class PremiumReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PremiumRewardType type;             // CANJEABLE o SORTEO

    @Column(name = "points_required")
    private int pointsRequired = 0;             // solo para CANJEABLE

    private Integer stock;                      // null = ilimitado para sorteos

    @Column(name = "draw_date")
    private LocalDateTime drawDate;             // solo para SORTEO

    @Column(name = "draw_executed")
    private boolean drawExecuted = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "winner_user_id")
    @JsonIgnoreProperties({"password", "verificationToken", "resetPasswordToken",
            "subscriptions", "redemptions", "reviews", "comments"})
    private User winner;                        // ganador del sorteo

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(length = 200)
    private String partner;                     // socio/proveedor

    @Column(length = 500)
    private String website;                     // sitio web del socio

    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private java.math.BigDecimal discountValue;

    @Column(name = "discount_type", length = 10)
    private String discountType; // PERCENTAGE o FIXED

    @Column(name = "discount_code", length = 50)
    private String discountCode;

    @Column(name = "experience_type", length = 200)
    private String experienceType;

    @Column(length = 300)
    private String location;

    @Column(name = "event_date")
    private LocalDateTime eventDate;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    // --- Merchandising ---
    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String material;

    @Column(length = 100)
    private String color;

    @Column(length = 50)
    private String size;

    @Column(length = 100)
    private String dimensions;

    @Column(length = 50)
    private String weight;

    @Column(length = 100)
    private String origin;

    @Column(name = "units_included", length = 50)
    private String unitsIncluded;

    @Column(length = 30)
    private String condition; // NUEVO, REACONDICIONADO

    // --- Entrada de cine ---
    @Column(name = "cinema_chain", length = 100)
    private String cinemaChain;

    @Column(name = "cinema_format", length = 20)
    private String cinemaFormat; // 2D, 3D, IMAX, 4DX, OTROS

    @Column(name = "cinema_restrictions", columnDefinition = "TEXT")
    private String cinemaRestrictions;

    @Column(name = "tickets_included")
    private Integer ticketsIncluded;

    @Column(name = "includes_snack")
    private Boolean includesSnack;

    // --- Descuento ---
    @Column(name = "discount_channel", length = 100)
    private String discountChannel; // WEB, APP, LOCAL, TELEFONICO

    @Column(name = "minimum_purchase", precision = 10, scale = 2)
    private java.math.BigDecimal minimumPurchase;

    @Column(name = "applicable_products", columnDefinition = "TEXT")
    private String applicableProducts;

    @Column(name = "stackable")
    private Boolean stackable;

    // --- Experiencia ---
    @Column(length = 100)
    private String duration;

    @Column(name = "includes_transport")
    private Boolean includesTransport;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "companion_allowed")
    private Boolean companionAllowed;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean hasStock() {
        return stock == null || stock > 0;
    }

    public void decreaseStock() {
        if (stock != null && stock > 0) {
            stock--;
        }
    }
}

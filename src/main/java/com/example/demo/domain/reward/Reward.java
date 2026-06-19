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

    @Column(nullable = false)
    private Boolean deleted = false;

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

    @Column(name = "draw_date")
    private LocalDateTime drawDate;

    @Column(name = "draw_executed")
    private boolean drawExecuted = false;

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
    private String discountChannel; // WEB, APP, LOCAL, TELEFÓNICO

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

    // --- Entrega (comunes a todos los tipos) ---
    @Column(name = "delivery_method", length = 50)
    private String deliveryMethod; // RETIRO_PRESENCIAL, ENTREGA_DIGITAL, COORDINACION_TERCERO, ENVIO_DOMICILIO

    @Column(name = "pickup_point", length = 300)
    private String pickupPoint;

    @Column(name = "delivery_cost", length = 50)
    private String deliveryCost; // GRATUITO, A_CARGO_GANADOR, COORDINAR_TERCERO

    // --- Descuento extra ---
    @Column(name = "redeem_method", length = 50)
    private String redeemMethod; // CODIGO_DIGITAL, LINK_PROMOCIONAL, PRESENTAR_USUARIO, AUTOMATICO

    // --- Experiencia extra ---
    @Column(name = "requires_confirmation")
    private Boolean requiresConfirmation;

    @Column(name = "transferable")
    private Boolean transferable;

    @Column(length = 200)
    private String organizer;

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
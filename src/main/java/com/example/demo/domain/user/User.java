package com.example.demo.domain.user;

import com.example.demo.domain.redemption.Redemption;
import com.example.demo.domain.review.Review;
import com.example.demo.domain.sweepstake.SweepstakeEntry;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    @Column(name = "reset_password_token", length = 255)
    private String resetPasswordToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "available_points", nullable = false)
    private int availablePoints = 0;

    @Column(name = "accumulated_points", nullable = false)
    private int accumulatedPoints = 0;

    @Column(name = "total_redeemed_points", nullable = false)
    private int totalRedeemedPoints = 0;

    @Column(name = "free_monthly_cap")
    private Integer freeMonthlyCapOverride;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "dni", unique = true, length = 20)
    private String dni;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete = true;

    private boolean active = true;

    @Column(nullable = false)
    private Boolean suspended = false;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    // @JsonIgnore en las tres: ninguna necesita viajar embebida dentro del JSON
    // de un User — cada una tiene sus propios endpoints dedicados. Sin esto,
    // cualquier endpoint que serialice un User (directo o embebido dentro de
    // otra entidad, como Publication) puede disparar una recursión infinita
    // vía la relación de vuelta hacia User (Review.user, etc.) y provocar
    // un StackOverflowError silencioso en la respuesta.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Review> reviews;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Redemption> redemptions;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<SweepstakeEntry> sweepstakeEntries;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_level", nullable = false)
    private UserLevel level = UserLevel.AMATEUR;

    @Column(name = "level_updated_at")
    private LocalDateTime levelUpdatedAt;

    @Column(name = "is_premium", nullable = false)
    private boolean premium = false;

    @Column(name = "premium_until")
    private LocalDateTime premiumUntil;

    @Column(name = "is_creator", nullable = false)
    private boolean creator = false;

    @Column(name = "creator_until")
    private LocalDateTime creatorUntil;

    @Column(name = "daily_comment_count", nullable = false)
    private int dailyCommentCount = 0;

    @Column(name = "last_comment_date")
    private java.time.LocalDate lastCommentDate;

    @Column(name = "daily_recommendation_count", nullable = false)
    private int dailyRecommendationCount = 0;

    @Column(name = "last_recommendation_date")
    private java.time.LocalDate lastRecommendationDate;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "bio_titulo", length = 50)
    private String bioTitulo;

    @Column(name = "bio_texto", length = 200)
    private String bioTexto;

    @Column(name = "profile_visibility", length = 20, nullable = false)
    private String profileVisibility = "PUBLIC";

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(name = "sexo", length = 1)
    private String sexo;

    @Column(name = "provincia", length = 100)
    private String provincia;

    @Column(name = "localidad", length = 100)
    private String localidad;

    public boolean isPrivate() {
        return "PRIVATE".equals(this.profileVisibility);
    }

    // ==============================================
    // MÉTODOS DE NEGOCIO (NO GETTERS/SETTERS)
    // ==============================================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Suma puntos acumulados del mes en curso (no disponibles aún)
     */
    public void addAccumulatedPoints(int points) {
        this.accumulatedPoints += points;
    }

    /**
     * Descuenta puntos disponibles al momento del canje (FIFO se maneja en PointBatchService)
     * También incrementa el contador histórico de canjeados (base para insignias)
     */
    public void redeemPoints(int points) {
        if (this.availablePoints >= points) {
            this.availablePoints -= points;
            this.totalRedeemedPoints += points;
        } else {
            throw new IllegalStateException("Puntos disponibles insuficientes");
        }
    }

    /**
     * Suma puntos disponibles (llamado por el scheduler mensual al liberar lotes)
     */
    public void addAvailablePoints(int points) {
        this.availablePoints += points;
    }

    /**
     * Resta puntos acumulados (llamado por el scheduler al liberar — mueve acumulados a disponibles)
     */
    public void clearAccumulatedPoints(int pointsReleased) {
        this.accumulatedPoints = Math.max(0, this.accumulatedPoints - pointsReleased);
    }

    /**
     * @deprecated Usar addAccumulatedPoints() o redeemPoints() según el contexto
     */
    @Deprecated
    public void addPoints(int points) {
        this.accumulatedPoints += points;
    }

    /**
     * @deprecated Usar redeemPoints() para canjes
     */
    @Deprecated
    public void subtractPoints(int points) {
        if (this.availablePoints >= points) {
            this.availablePoints -= points;
            this.totalRedeemedPoints += points;
        } else {
            throw new IllegalStateException("Puntos disponibles insuficientes");
        }
    }

    /**
     * Compatibilidad temporal — devuelve puntos disponibles
     * @deprecated Usar getAvailablePoints()
     */
    @Deprecated
    public int getTotalPoints() {
        return this.availablePoints;
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public boolean isSuspended() {
        return suspended != null && suspended;
    }

    public void suspend(String reason) {
        this.suspended = true;
        this.suspensionReason = reason;
        this.suspendedAt = LocalDateTime.now();
        this.active = false;
    }

    public void unsuspend() {
        this.suspended = false;
        this.suspensionReason = null;
        this.suspendedAt = null;
        this.active = true;
    }

    public void updateAvatar(String newAvatarUrl) {
        this.avatarUrl = newAvatarUrl;
        this.updatedAt = LocalDateTime.now();
    }

    public void removeAvatar() {
        this.avatarUrl = null;
        this.updatedAt = LocalDateTime.now();
    }

    public String getEffectiveAvatarUrl() {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            return avatarUrl;
        }
        return profileImageUrl;
    }

    /**
     * Actualiza el nivel basándose en los puntos históricos canjeados.
     * El nivel es una insignia acumulativa — nunca baja.
     */
    public boolean updateLevelBasedOnPoints() {
        UserLevel newLevel = UserLevel.getLevelByPoints(this.totalRedeemedPoints);
        // El nivel solo sube, nunca baja
        if (newLevel.ordinal() > this.level.ordinal()) {
            this.level = newLevel;
            this.levelUpdatedAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }

    public void setLevelManually(UserLevel newLevel) {
        if (newLevel != null) {
            this.level = newLevel;
            this.levelUpdatedAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
        }
    }

    public UserLevel getNextLevel() {
        return this.level.getNextLevel();
    }

    public int getPointsToNextLevel() {
        return this.level.getPointsToNextLevel(this.totalRedeemedPoints);
    }

    public double getProgressToNextLevel() {
        UserLevel next = this.level.getNextLevel();
        if (next == null) {
            return 100.0;
        }
        int currentLevelMin = this.level.getMinPoints();
        int nextLevelMin = next.getMinPoints();
        int pointsInCurrentLevel = this.totalRedeemedPoints - currentLevelMin;
        int pointsNeededForNext = nextLevelMin - currentLevelMin;
        return (double) pointsInCurrentLevel / pointsNeededForNext * 100;
    }

    /**
     * Retorna el tope mensual de liberación.
     * FREE: 5.000 pts (o valor personalizado). PREMIUM: sin tope (null).
     */
    public Integer getEffectiveMonthlyCap() {
        if (this.premium) return null;
        return freeMonthlyCapOverride != null ? freeMonthlyCapOverride : 5000;
    }

    public boolean isActivePremium() {
        return premium && premiumUntil != null && premiumUntil.isAfter(LocalDateTime.now());
    }

    /**
     * Creator ahora es una suscripción con vencimiento real (igual que Premium),
     * no un flag manual on/off. Independiente de Premium: un usuario puede
     * tener ambas activas a la vez.
     */
    public boolean isActiveCreator() {
        return creator && creatorUntil != null && creatorUntil.isAfter(LocalDateTime.now());
    }

    /**
     * Formatos permitidos según plan. FREE: solo texto · PREMIUM: texto + imagen
     * · CREATOR: texto + imagen + video. Creator es superset de Premium en
     * formatos, por eso alcanza con "es Creator" para habilitar video sin
     * necesitar también Premium.
     */
    public boolean puedeSubirImagen() {
        return isActivePremium() || isActiveCreator();
    }

    public boolean puedeSubirVideo() {
        return isActiveCreator();
    }

    /**
     * Techo técnico antiabuso — parejo para todos los planes, no es un
     * beneficio de ningún tier ni se comunica como límite de producto.
     * Existe solo para proteger la infraestructura ante un volumen anómalo.
     */
    public static final int DAILY_PUBLICATION_ANTIABUSE_LIMIT = 20;

    /**
     * Cuántas publicaciones del día otorgan puntos, según plan. A partir de
     * esa cantidad, el usuario sigue publicando libre (dentro del techo
     * antiabuso) pero sin sumar puntos por esas publicaciones extra.
     * Creator es un plan puramente funcional (video/reels, más imágenes por
     * publicación, más cupo con puntos vía Premium si también lo tiene) —
     * no genera puntos por sí solo, para no opacar a Premium, que es el
     * plan de "recompensa". Si el usuario tiene ambos activos, suma por
     * Premium igual.
     * FREE: 0 · PREMIUM: 10 · CREATOR (solo): 0
     */
    /**
     * Cuántas publicaciones del día otorgan puntos, según plan.
     * FREE: 1 (a 50pts, mitad que Premium — funciona como "cebo" real, no
     * solo simbólico) · PREMIUM: 10 (a 100pts) · CREATOR (solo, sin Premium): 0,
     * es un plan puramente funcional, no de recompensa.
     */
    /**
     * Creator es neutro respecto a puntos, no sustitutivo — no le agrega
     * nada al cupo (a diferencia de Premium), pero tampoco le resta nada
     * a lo que ya tenía como Free. Por eso no hay rama explícita para
     * Creator acá: si no es Premium, siempre cae en el comportamiento
     * base de Free (1), tenga o no Creator activo además.
     */
    public int getDailyPublicationsConPuntosLimit() {
        if (isActivePremium()) return 10;
        return 1;
    }
}
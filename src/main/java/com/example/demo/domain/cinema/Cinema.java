package com.example.demo.domain.cinema;

import com.example.demo.domain.sweepstake.Sweepstake;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Espejo local del directorio de cines de agendadecine.com — esta entidad
 * ya NO se carga a mano. Se puebla y actualiza por sincronización periódica
 * contra el directorio completo del sitio (324 cines, todas las provincias
 * del país), usando agendaDeCineComplexId como clave de import: si ya existe
 * una fila con ese complexId, se actualiza; si no, se crea una nueva.
 *
 * Por qué mantenemos una copia propia en vez de consultar agendadecine.com
 * en vivo cada vez: (1) si el sitio está caído o lento, servicios como
 * "listar cines" siguen funcionando con los últimos datos sincronizados;
 * (2) esta entidad es el punto de anclaje para contenido propio de
 * Cinemarketer sobre un cine — sorteos, moderación, etc. — que no tiene
 * sentido ni lugar en la fuente externa.
 *
 * Campos SINCRONIZADOS (el import los pisa siempre con lo último de la
 * fuente): name, chain, chainId, province, city, address, lat, lng, phone,
 * website, agendaDeCineSlug.
 *
 * Campos PROPIOS de Cinemarketer (el import NUNCA los toca, solo existen
 * acá): description, images, active.
 */
@Entity
@Table(name = "cinemas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cinema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Identidad en la fuente (agendadecine.com) ---

    // ID de la SUCURSAL (no de la cadena) tal como lo expone agendadecine.com
    // — ej. "63" para Cinépolis Arena Maipú, "67" para Cinépolis Mendoza
    // Shopping. Es la clave real de import/sincronización: estable, no se
    // rompe si el sitio cambia el texto del nombre, y nunca agrupa dos
    // sucursales reales bajo un mismo valor (cada local de una misma cadena
    // tiene su propio complexId, aunque compartan nombre de cadena — ej. los
    // distintos "Cine Dinosaurio").
    @Column(nullable = false, unique = true, length = 20)
    private String agendaDeCineComplexId;

    // Slug legible que usa agendadecine.com en sus propias URLs
    // (ej. "cine-ambassador") — útil para armar links directos al sitio
    // origen si hiciera falta, o como identificador humano de respaldo.
    @Column(length = 150)
    private String agendaDeCineSlug;

    // --- Datos sincronizados ---

    @Column(nullable = false, length = 200)
    private String name;

    // Cadena a la que pertenece: "Cinemark", "Cinépolis", "Cinemacenter",
    // "Showcase", "Atlas", "Independiente"...
    @Column(length = 100)
    private String chain;

    // ID de la cadena tal como lo expone agendadecine.com (ej. "2" para
    // Cinemark) — útil a futuro para filtrar/agrupar por cadena sin
    // depender de que el nombre de texto coincida exacto.
    @Column(length = 20)
    private String chainId;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 255)
    private String address;

    private Double lat;
    private Double lng;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String website;

    // --- Contenido propio de Cinemarketer (nunca lo toca el sync) ---

    @Column(length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "cinema_images", joinColumns = @JoinColumn(name = "cinema_id"))
    @Column(name = "image_url", length = 500)
    private List<String> images;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Última vez que el sync trajo y pisó los datos de este cine desde
    // agendadecine.com — útil para detectar cines que dejaron de aparecer
    // en el directorio (el sync podría marcarlos active=false si hace
    // tiempo que no se actualizan, en vez de borrarlos).
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    // Relación con sorteos (un cine puede tener varios sorteos)
    @OneToMany(mappedBy = "targetCinema")
    private List<Sweepstake> sweepstakes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
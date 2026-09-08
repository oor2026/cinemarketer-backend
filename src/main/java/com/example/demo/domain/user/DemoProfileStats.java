package com.example.demo.domain.user;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "demo_profile_stats")
@Data
public class DemoProfileStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    private String marca;
    private String nivel;
    private Integer votaciones = 0;
    private Integer comentarios = 0;
    private Integer publicaciones = 0;
    private Integer seguidores = 0;
    private Integer seguidos = 0;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "banner_url")
    private String bannerUrl;
}
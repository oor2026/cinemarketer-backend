package com.example.demo.web.controllers;

import com.example.demo.infrastructure.external.tmdb.LoginPostersService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pósters para el muro del login mobile. Público (no pide sesión): /api/public/** ya está permitido en SecurityConfig.
 * GET /api/public/login-posters  →  { "posters": ["/abc.jpg", ...] }
 */
@RestController
@RequestMapping("/api/public")
public class LoginPostersController {

    private final LoginPostersService servicio;

    public LoginPostersController(LoginPostersService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/login-posters")
    public ResponseEntity<Map<String, Object>> getLoginPosters() {
        List<String> posters = servicio.obtener();
        // Si hay lista, el navegador y el CDN pueden guardarla 30 minutos. Si no hay (TMDB caído), que no se la queden.
        CacheControl cache = posters.isEmpty() ? CacheControl.noStore() : CacheControl.maxAge(30, TimeUnit.MINUTES).cachePublic();
        return ResponseEntity.ok().cacheControl(cache).body(Map.of("posters", posters));
    }
}

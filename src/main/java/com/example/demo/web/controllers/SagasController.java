package com.example.demo.web.controllers;

import com.example.demo.domain.mision.SagasCatalogo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/sagas — el universo de sagas (la misma lista que usa el asistente del feed). Con sesión, como el resto del feed.
 * Respuesta: { "version": 1, "sagas": [ { "id", "nombre", "coleccion", "peliculas": [ids de TMDB] } ] }
 */
@RestController
@RequestMapping("/api/sagas")
public class SagasController {

    private final SagasCatalogo catalogo;

    public SagasController(SagasCatalogo catalogo) { this.catalogo = catalogo; }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar() {
        List<Map<String, Object>> sagas = new ArrayList<>();
        for (SagasCatalogo.Saga s : catalogo.todas()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("nombre", s.nombre());
            m.put("coleccion", s.coleccion());
            m.put("peliculas", s.peliculas());
            sagas.add(m);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("version", catalogo.version());
        cuerpo.put("sagas", sagas);
        return ResponseEntity.ok(cuerpo);
    }
}

package com.example.demo.application.services;

import com.example.demo.domain.publication.Hashtag;
import com.example.demo.domain.publication.HashtagRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HashtagService {

    private final HashtagRepository hashtagRepository;

    public HashtagService(HashtagRepository hashtagRepository) {
        this.hashtagRepository = hashtagRepository;
    }

    // Llamar al crear una publicación (o al aprobarse, según decidas), con
    // el array de hashtags YA normalizado por normalizarHashtags().
    @Transactional
    public void incrementar(String[] hashtags) {
        if (hashtags == null) return;
        for (String nombre : hashtags) {
            Hashtag h = hashtagRepository.findByNombre(nombre)
                    .orElseGet(() -> {
                        Hashtag nuevo = new Hashtag();
                        nuevo.setNombre(nombre);
                        nuevo.setUsageCount(0);
                        return nuevo;
                    });
            h.setUsageCount(h.getUsageCount() + 1);
            hashtagRepository.save(h);
        }
    }

    // Llamar al ocultar/eliminar una publicación.
    @Transactional
    public void decrementar(String[] hashtags) {
        if (hashtags == null) return;
        for (String nombre : hashtags) {
            hashtagRepository.findByNombre(nombre).ifPresent(h -> {
                h.setUsageCount(Math.max(0, h.getUsageCount() - 1));
                hashtagRepository.save(h);
            });
        }
    }

    // Llamar al editar una publicación: solo ajusta la diferencia entre el
    // array viejo y el nuevo, sin restar/sumar de más sobre los que no cambiaron.
    @Transactional
    public void ajustarPorEdicion(String[] anteriores, String[] nuevos) {
        Set<String> viejosSet = anteriores == null ? new HashSet<>() : new HashSet<>(Arrays.asList(anteriores));
        Set<String> nuevosSet = nuevos == null ? new HashSet<>() : new HashSet<>(Arrays.asList(nuevos));

        List<String> agregados = nuevosSet.stream().filter(h -> !viejosSet.contains(h)).toList();
        List<String> quitados = viejosSet.stream().filter(h -> !nuevosSet.contains(h)).toList();

        incrementar(agregados.toArray(new String[0]));
        decrementar(quitados.toArray(new String[0]));
    }

    // Para el autocompletado: normaliza el prefijo igual que normalizarHashtags()
    // de PublicationService (minúsculas, sin "#"), y devuelve los más usados que matchean.
    public List<Map<String, Object>> sugerir(String prefijo, int limite) {
        if (prefijo == null || prefijo.isBlank()) return List.of();
        String prefijoNormalizado = prefijo.trim().toLowerCase().replaceFirst("^#", "");

        List<Hashtag> resultados = hashtagRepository.findTopByPrefijo(prefijoNormalizado, PageRequest.of(0, limite));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Hashtag h : resultados) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", h.getNombre());
            m.put("usageCount", h.getUsageCount());
            out.add(m);
        }
        return out;
    }
}
package com.example.demo.application.services;

import com.example.demo.domain.juego.JuegoDescalificacion;
import com.example.demo.domain.juego.JuegoDescalificacionRepository;
import com.example.demo.domain.juego.JuegoIntento;
import com.example.demo.domain.juego.JuegoIntentoRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Ranking acumulado de una campaña: suma los aciertos y el tiempo de todos
 * los días. Orden: más aciertos, después menos tiempo, después quien jugó
 * primero. Lo usan la landing pública y el cierre del admin.
 */
@Service
public class JuegoRankingService {

    private final JuegoIntentoRepository intentoRepository;
    private final JuegoDescalificacionRepository descalificacionRepository;
    private final UserRepository userRepository;

    public JuegoRankingService(JuegoIntentoRepository intentoRepository,
                               JuegoDescalificacionRepository descalificacionRepository,
                               UserRepository userRepository) {
        this.intentoRepository = intentoRepository;
        this.descalificacionRepository = descalificacionRepository;
        this.userRepository = userRepository;
    }

    // posicion es null para los descalificados (no ocupan lugar).
    public record Fila(Integer posicion, Long userId, int aciertos, long tiempoMs, int diasJugados,
                       LocalDateTime primeraPartida, String motivoDescalificacion) {
        public boolean descalificado() { return motivoDescalificacion != null; }
    }

    // Lo que se muestra en público: nombre corto ("Juan P.") y avatar.
    public record FilaPublica(int posicion, String nombre, String avatar, int aciertos,
                              long tiempoMs, int diasJugados, boolean soyYo) {}

    @Transactional(readOnly = true)
    public List<Fila> calcular(Long campaniaId) {
        Map<Long, String> descalificados = new HashMap<>();
        for (JuegoDescalificacion d : descalificacionRepository.findByCampaniaId(campaniaId)) {
            descalificados.put(d.getUserId(), d.getMotivo());
        }

        Map<Long, int[]> aciertosYDias = new HashMap<>();
        Map<Long, Long> tiempos = new HashMap<>();
        Map<Long, LocalDateTime> primeras = new HashMap<>();
        for (JuegoIntento i : intentoRepository.findByCampaniaId(campaniaId)) {
            int[] ad = aciertosYDias.computeIfAbsent(i.getUserId(), k -> new int[2]);
            ad[0] += i.getAciertos();
            ad[1]++;
            tiempos.merge(i.getUserId(), i.getTiempoTotalMs(), Long::sum);
            primeras.merge(i.getUserId(), i.getIniciadoAt(), (a, b) -> a.isBefore(b) ? a : b);
        }

        List<Long> orden = new ArrayList<>(aciertosYDias.keySet());
        orden.sort(Comparator
                .comparing((Long u) -> -aciertosYDias.get(u)[0])
                .thenComparing(tiempos::get)
                .thenComparing(primeras::get)
                .thenComparing(u -> u));

        List<Fila> filas = new ArrayList<>();
        int posicion = 0;
        for (Long u : orden) {
            String motivo = descalificados.get(u);
            Integer pos = motivo == null ? ++posicion : null;
            filas.add(new Fila(pos, u, aciertosYDias.get(u)[0], tiempos.get(u),
                    aciertosYDias.get(u)[1], primeras.get(u), motivo));
        }
        return filas;
    }

    // Los primeros "cuantos" sin descalificados, listos para mostrar.
    @Transactional(readOnly = true)
    public List<FilaPublica> top(List<Fila> filas, int cuantos, Long miUserId) {
        List<Fila> visibles = filas.stream().filter(f -> !f.descalificado()).limit(cuantos).toList();
        Map<Long, User> usuarios = usuarios(visibles.stream().map(Fila::userId).toList());
        List<FilaPublica> lista = new ArrayList<>();
        for (Fila f : visibles) {
            User u = usuarios.get(f.userId());
            lista.add(new FilaPublica(f.posicion(), nombrePublico(u), avatar(u), f.aciertos(),
                    f.tiempoMs(), f.diasJugados(), f.userId().equals(miUserId)));
        }
        return lista;
    }

    public Map<Long, User> usuarios(Collection<Long> ids) {
        Map<Long, User> mapa = new HashMap<>();
        if (ids.isEmpty()) return mapa;
        for (User u : userRepository.findAllById(ids)) mapa.put(u.getId(), u);
        return mapa;
    }

    // "Juan Pérez" → "Juan P.". El nombre completo no se publica.
    public static String nombrePublico(User u) {
        if (u == null || u.getName() == null || u.getName().isBlank()) return "Cinéfilo/a";
        String[] partes = u.getName().trim().split("\\s+");
        if (partes.length == 1) return partes[0];
        String apellido = partes[partes.length - 1];
        return partes[0] + " " + apellido.substring(0, 1).toUpperCase() + ".";
    }

    public static String avatar(User u) {
        if (u == null) return null;
        if (u.getAvatarUrl() != null && !u.getAvatarUrl().isBlank()) return u.getAvatarUrl();
        return u.getProfileImageUrl();
    }
}

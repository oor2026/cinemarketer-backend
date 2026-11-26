package com.example.demo.application.services;

import com.example.demo.domain.juego.JuegoDescalificacion;
import com.example.demo.domain.juego.JuegoDescalificacionRepository;
import com.example.demo.domain.juego.JuegoIntento;
import com.example.demo.domain.juego.JuegoIntentoRepository;
import com.example.demo.domain.juego.JuegoParticipante;
import com.example.demo.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Ranking acumulado de una campaña: suma los aciertos y el tiempo de todos
 * los días. Orden: más aciertos, después menos tiempo, después quien jugó
 * primero. Lo usan la landing pública y el cierre del admin.
 * Funciona igual con o sin registro: todo se cuenta por participante.
 */
@Service
public class JuegoRankingService {

    private final JuegoIntentoRepository intentoRepository;
    private final JuegoDescalificacionRepository descalificacionRepository;
    private final JuegoParticipanteService participanteService;

    public JuegoRankingService(JuegoIntentoRepository intentoRepository,
                               JuegoDescalificacionRepository descalificacionRepository,
                               JuegoParticipanteService participanteService) {
        this.intentoRepository = intentoRepository;
        this.descalificacionRepository = descalificacionRepository;
        this.participanteService = participanteService;
    }

    // posicion es null para los descalificados (no ocupan lugar).
    public record Fila(Integer posicion, Long participanteId, int aciertos, long tiempoMs, int diasJugados,
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
            descalificados.put(d.getParticipanteId(), d.getMotivo());
        }

        Map<Long, int[]> aciertosYDias = new HashMap<>();
        Map<Long, Long> tiempos = new HashMap<>();
        Map<Long, LocalDateTime> primeras = new HashMap<>();
        for (JuegoIntento i : intentoRepository.findByCampaniaId(campaniaId)) {
            int[] ad = aciertosYDias.computeIfAbsent(i.getParticipanteId(), k -> new int[2]);
            ad[0] += i.getAciertos();
            ad[1]++;
            tiempos.merge(i.getParticipanteId(), i.getTiempoTotalMs(), Long::sum);
            primeras.merge(i.getParticipanteId(), i.getIniciadoAt(), (a, b) -> a.isBefore(b) ? a : b);
        }

        List<Long> orden = new ArrayList<>(aciertosYDias.keySet());
        orden.sort(Comparator
                .comparing((Long p) -> -aciertosYDias.get(p)[0])
                .thenComparing(tiempos::get)
                .thenComparing(primeras::get)
                .thenComparing(p -> p));

        List<Fila> filas = new ArrayList<>();
        int posicion = 0;
        for (Long p : orden) {
            String motivo = descalificados.get(p);
            Integer pos = motivo == null ? ++posicion : null;
            filas.add(new Fila(pos, p, aciertosYDias.get(p)[0], tiempos.get(p),
                    aciertosYDias.get(p)[1], primeras.get(p), motivo));
        }
        return filas;
    }

    // Los primeros "cuantos" sin descalificados, listos para mostrar.
    @Transactional(readOnly = true)
    public List<FilaPublica> top(List<Fila> filas, int cuantos, Long miParticipanteId) {
        List<Fila> visibles = filas.stream().filter(f -> !f.descalificado()).limit(cuantos).toList();
        Map<Long, JuegoParticipante> participantes = participanteService.participantes(
                visibles.stream().map(Fila::participanteId).toList());
        Map<Long, User> usuarios = participanteService.usuariosDe(participantes.values());
        List<FilaPublica> lista = new ArrayList<>();
        for (Fila f : visibles) {
            JuegoParticipante p = participantes.get(f.participanteId());
            User u = p == null || p.getUserId() == null ? null : usuarios.get(p.getUserId());
            lista.add(new FilaPublica(f.posicion(), nombrePublico(p, u), avatar(u), f.aciertos(),
                    f.tiempoMs(), f.diasJugados(), f.participanteId().equals(miParticipanteId)));
        }
        return lista;
    }

    // Con cuenta se usa el nombre actual de la cuenta; sin cuenta, el que escribió al anotarse.
    public static String nombrePublico(JuegoParticipante p, User u) {
        if (u != null && u.getName() != null && !u.getName().isBlank()) return nombrePublico(u.getName());
        return nombrePublico(p == null ? null : p.getNombre());
    }

    // "Juan Pérez" → "Juan P.". El nombre completo no se publica.
    public static String nombrePublico(String nombre) {
        if (nombre == null || nombre.isBlank()) return "Participante";
        String[] partes = nombre.trim().split("\\s+");
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

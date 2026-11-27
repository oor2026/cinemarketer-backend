package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionLogica.Mision;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Lo que «Reclamar» necesita de la base: el historial del usuario (votos y comentarios de antes del sistema) y lo que ya se evaluó de él.
 * La implementación real es HistorialRepositorio (SQL); las pruebas usan una en memoria.
 */
public interface HistorialDatos {

    /** Una cosa que el usuario hizo. tipo: VOTO o COMENTARIO · contenido: PELICULA o SERIE · targetId: el id de TMDB. */
    record Evento(String tipo, String contenido, long targetId, LocalDateTime cuando) {}

    /** Lo que se encontró en el historial para una misión. estado: PENDIENTE · APLICADO · RECLAMADO · IGNORADO. */
    record Registro(long misionId, String estado, int base, LocalDateTime calculadoAt) {}

    /** Todos los votos y comentarios (de películas y series) que hizo el usuario, sin los comentarios eliminados u ocultos. */
    List<Evento> eventos(long userId);

    Map<Long, Registro> registros(long userId);

    /**
     * Cuándo se creó la fila permanente del usuario en cada misión: es el momento de la primera actividad que el sistema contó en vivo.
     * Todo lo anterior a eso es historial; lo posterior ya lo contó el sistema.
     */
    Map<Long, LocalDateTime> primerAvanceEnVivo(long userId);

    /** Guarda lo evaluado. Una fila PENDIENTE o RECLAMADO nunca se pisa; una IGNORADO sí. */
    void guardar(long userId, long misionId, String estado, int base, LocalDateTime ahora);

    /**
     * Como guardar, pero pisa también lo ya evaluado (APLICADO, PENDIENTE o IGNORADO). Solo una RECLAMADO queda como está. Lo usan las sagas, cuyo
     * estado se recalcula cada vez con los votos del usuario.
     */
    default void reevaluar(long userId, long misionId, String estado, int base, LocalDateTime ahora) { guardar(userId, misionId, estado, base, ahora); }

    /** true si estaba PENDIENTE y pasó a RECLAMADO (false si ya lo estaba: no se otorga dos veces). */
    boolean marcarReclamado(long userId, long misionId, LocalDateTime ahora);

    int contarPendientes(long userId);

    /**
     * Misiones rotativas activas que el usuario NO tiene asignadas, de las que todavía no tiene el sello y que hay que evaluar:
     * las que nunca se evaluaron o las IGNORADO cuya última evaluación es anterior a {@code repasoDesde}.
     */
    List<Mision> rotativasParaEvaluar(long userId, LocalDateTime repasoDesde);

    /** Las misiones con esos ids (activas), para mostrar las que quedaron pendientes de reclamar. */
    List<Mision> misionesPorIds(List<Long> ids);

    /** El id del objetivo de rotación de esa misión, o null si no es rotativa. */
    Long objetivoDeMision(long misionId);
}

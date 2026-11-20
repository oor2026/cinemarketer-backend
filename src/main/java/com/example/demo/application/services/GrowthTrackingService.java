package com.example.demo.application.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Métricas de crecimiento del Panel de seguimiento (v1.5):
 *  - visitas al perfil (quién abrió el perfil de otra persona, una vez por día);
 *  - visitas a una publicación llegando desde un link compartido (?ref=u<id>);
 *  - registros de cuentas nuevas atribuidos a esas visitas.
 * Todo es "mejor esfuerzo": nada de esto lanza excepciones hacia afuera, para no romper el
 * registro de usuarios ni la carga de un perfil.
 */
@Service
public class GrowthTrackingService {

    private static final Logger log = LoggerFactory.getLogger(GrowthTrackingService.class);

    private static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Pattern CLAVE_ANONIMA = Pattern.compile("^[A-Za-z0-9-]{8,40}$");
    private static final Pattern REF = Pattern.compile("^u(\\d{1,12})$");
    /** Un registro se atribuye a la última visita por link de hasta 30 días atrás. */
    private static final int VENTANA_DIAS = 30;

    private final NamedParameterJdbcTemplate jdbc;

    public GrowthTrackingService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Autor de la publicación si está ACTIVA (visible); vacío si no existe o no está visible. */
    private List<Long> autorSiActiva(Long publicationId) {
        return jdbc.queryForList(
                "SELECT user_id FROM publications WHERE id = :id AND hidden = false AND moderation_status = 'APPROVED'",
                new MapSqlParameterSource("id", publicationId), Long.class);
    }

    /** Visita al perfil de otra persona. Una por visitante y día; la propia no cuenta. */
    public void registrarVisitaPerfil(Long viewerId, Long profileUserId) {
        if (viewerId == null || profileUserId == null || viewerId.equals(profileUserId)) return;
        try {
            jdbc.update("""
                    INSERT INTO profile_views (profile_user_id, viewer_id, view_day, created_at)
                    SELECT :perfil, :viewer, :dia, :ahora
                    WHERE EXISTS (SELECT 1 FROM users WHERE id = :perfil)
                    ON CONFLICT (profile_user_id, viewer_id, view_day) DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("perfil", profileUserId)
                            .addValue("viewer", viewerId)
                            .addValue("dia", Date.valueOf(LocalDate.now(ZONA_AR)))
                            .addValue("ahora", Timestamp.valueOf(LocalDateTime.now())));
        } catch (Exception e) {
            log.warn("No se pudo registrar la visita al perfil {}: {}", profileUserId, e.getMessage());
        }
    }

    /** Visita a una publicación desde un link compartido. La clave anónima es la misma de las vistas públicas. */
    public void registrarVisitaDesdeLink(Long publicationId, String claveAnonima, String ref) {
        if (publicationId == null || claveAnonima == null || !CLAVE_ANONIMA.matcher(claveAnonima).matches()) return;
        try {
            if (autorSiActiva(publicationId).isEmpty()) return;
            Long sharerId = null;
            if (ref != null) {
                Matcher m = REF.matcher(ref);
                if (m.matches()) sharerId = Long.valueOf(m.group(1));
            }
            jdbc.update("""
                    INSERT INTO publication_share_visits (publication_id, sharer_user_id, visitor_key, visit_day, created_at)
                    VALUES (:pid, :sharer, :clave, :dia, :ahora)
                    ON CONFLICT (publication_id, visitor_key, visit_day) DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("pid", publicationId)
                            .addValue("sharer", sharerId)
                            .addValue("clave", claveAnonima)
                            .addValue("dia", Date.valueOf(LocalDate.now(ZONA_AR)))
                            .addValue("ahora", Timestamp.valueOf(LocalDateTime.now())));
        } catch (Exception e) {
            log.warn("No se pudo registrar la visita por link de la publicación {}: {}", publicationId, e.getMessage());
        }
    }

    /**
     * Atribuye el registro de una cuenta nueva a una publicación. Solo cuenta si ese mismo navegador
     * (clave anónima) tiene una visita por link registrada a esa publicación en los últimos 30 días:
     * así nadie puede inventar una atribución con parámetros sueltos. El que compartió se toma de esa visita.
     * Nunca lanza excepciones: el alta de la cuenta no puede fallar por esto.
     */
    public void registrarRegistro(Long nuevoUserId, String publicationIdTexto, String claveAnonima) {
        if (nuevoUserId == null || publicationIdTexto == null || claveAnonima == null) return;
        try {
            Long publicationId = Long.valueOf(publicationIdTexto.trim());
            if (!CLAVE_ANONIMA.matcher(claveAnonima).matches()) return;
            jdbc.update("""
                    INSERT INTO publication_share_signups (publication_id, sharer_user_id, new_user_id, created_at)
                    SELECT v.publication_id, v.sharer_user_id, :nuevo, :ahora
                    FROM publication_share_visits v
                    JOIN publications p ON p.id = v.publication_id
                         AND p.hidden = false AND p.moderation_status = 'APPROVED'
                    WHERE v.publication_id = :pid AND v.visitor_key = :clave AND v.visit_day >= :minDia
                    ORDER BY v.visit_day DESC, v.id DESC
                    LIMIT 1
                    ON CONFLICT (new_user_id) DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("nuevo", nuevoUserId)
                            .addValue("pid", publicationId)
                            .addValue("clave", claveAnonima)
                            .addValue("minDia", Date.valueOf(LocalDate.now(ZONA_AR).minusDays(VENTANA_DIAS)))
                            .addValue("ahora", Timestamp.valueOf(LocalDateTime.now())));
        } catch (Exception e) {
            log.warn("No se pudo atribuir el registro del usuario {}: {}", nuevoUserId, e.getMessage());
        }
    }
}

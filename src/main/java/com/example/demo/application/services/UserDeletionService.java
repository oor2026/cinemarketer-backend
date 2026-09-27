package com.example.demo.application.services;

import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.sweepstake.SweepstakeEntryRepository;
import com.example.demo.domain.sweepstake.WinnerRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeletionService {

    private final WinnerRepository winnerRepository;
    private final SweepstakeEntryRepository sweepstakeEntryRepository;
    private final RedemptionRepository redemptionRepository;
    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UserDeletionService(
            WinnerRepository winnerRepository,
            SweepstakeEntryRepository sweepstakeEntryRepository,
            RedemptionRepository redemptionRepository,
            CommentRepository commentRepository,
            ReviewRepository reviewRepository,
            UserRepository userRepository) {
        this.winnerRepository = winnerRepository;
        this.sweepstakeEntryRepository = sweepstakeEntryRepository;
        this.redemptionRepository = redemptionRepository;
        this.commentRepository = commentRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void deleteAllUserData(User user) {
        Long userId = user.getId();

        // Usamos SQL nativo para todo para garantizar el orden de ejecución
        // sin que Hibernate reordene las operaciones en su ActionQueue.
        //
        // Reescrito por completo tras una auditoría real del schema
        // (information_schema.columns) — la versión anterior solo cubría
        // 13 de las ~53 referencias reales a usuario que existen hoy en
        // la base. admin_notification_campaigns.created_by y
        // sweepstakes.created_by quedan afuera a propósito: son texto
        // libre con el nombre/email del admin, no una FK real a users.id.

        borrarInteraccionesSimples(userId);
        borrarRecomendacionesSeguidosYReportesDeUsuario(userId);
        borrarComentariosYSusHijos(userId);
        borrarPublicacionesPropiasYAjenas(userId);
        borrarBloqueos(userId);
        borrarSoporte(userId);
        // Orden invertido a propósito: winners.redemption_id apunta a
        // redemptions — si se borran redemptions primero, cualquier
        // winner todavía sin borrar bloquea el DELETE (confirmado con un
        // error real).
        borrarSorteosYPremium(userId);
        borrarPuntosYCanjes(userId);
        desvincularHistorialDePago(userId);

        // Usuario — último de todos.
        entityManager.createNativeQuery("DELETE FROM users WHERE id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    /**
     * Tablas de un solo nivel (user_id → fila propia), sin hijos ni
     * complicaciones — la actividad "de consumo" del usuario: qué vio,
     * qué votó, qué se saltó, sus reviews, su watchlist, sus notificaciones.
     */
    private void borrarInteraccionesSimples(Long userId) {
        String[] tablasSimples = {
                "trivia_attempts", "trivia_preguntas_vistas",
                "trivia_series_attempts", "trivia_series_preguntas_vistas",
                "voto_relampago_omitidas", "voto_relampago_omitidas_series",
                "watchlist", "series_watchlist",
                "spoiler_accepted", "series_spoiler_accepted",
                "no_me_interesa", "no_me_interesa_serie",
                "movie_expectations", "notifications", "push_subscriptions",
                "point_accumulation_log", "point_batches",
                "demo_profile_stats", "espiritu_snapshot", "gusto_historial",
                "draw_results",
                "reviews", "series_reviews",
        };
        for (String tabla : tablasSimples) {
            entityManager.createNativeQuery("DELETE FROM " + tabla + " WHERE user_id = :uid")
                    .setParameter("uid", userId).executeUpdate();
        }
    }

    /**
     * Comentarios propios del usuario, en ambos ámbitos (películas y
     * series) — hijos primero: reportes de respuestas → respuestas →
     * reacciones/reportes del comentario → el comentario en sí.
     */
    private void borrarComentariosYSusHijos(Long userId) {
        // -- Películas --
        // comment_reactions tiene 2 columnas de enlace (comment_id Y
        // reply_id, según si la reacción es al comentario o a una
        // respuesta) — confirmado con el schema real. Van primero, antes
        // de borrar las respuestas/comentarios que referencian.
        entityManager.createNativeQuery(
                        "DELETE FROM comment_reactions WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM comments WHERE user_id = :uid) " +
                                "OR reply_id IN (SELECT id FROM comment_replies WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM comments WHERE user_id = :uid))")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM comment_reply_reports WHERE reporter_user_id = :uid " +
                                "OR reply_id IN (SELECT id FROM comment_replies WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM comments WHERE user_id = :uid))")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM comment_replies WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM comments WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM comment_reports WHERE reporter_user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM comments WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM comments WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // -- Series (misma estructura, mismo orden) --
        entityManager.createNativeQuery(
                        "DELETE FROM series_comment_reactions WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM series_comments WHERE user_id = :uid) " +
                                "OR reply_id IN (SELECT id FROM series_comment_replies WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM series_comments WHERE user_id = :uid))")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM series_comment_reply_reports WHERE reporter_user_id = :uid " +
                                "OR reply_id IN (SELECT id FROM series_comment_replies WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM series_comments WHERE user_id = :uid))")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM series_comment_replies WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM series_comments WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM series_comment_reports WHERE reporter_user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM series_comments WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM series_comments WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    /**
     * El caso más delicado: si este usuario publicó algo, OTRAS personas
     * pueden tener comentarios/reacciones/votos/reportes colgando de esa
     * publicación — hay que borrar esas filas ajenas antes de poder
     * borrar la publicación en sí. Además, las interacciones que ESTE
     * usuario hizo en publicaciones ajenas también se limpian por
     * separado (con user_id propio, no ligadas a sus publicaciones).
     */
    private static final String ARBOL_COMENTARIOS_A_BORRAR =
            "WITH RECURSIVE target AS ( " +
                    "  SELECT id FROM publication_comments " +
                    "  WHERE user_id = :uid OR publication_id IN (SELECT id FROM publications WHERE user_id = :uid) " +
                    "  UNION " +
                    "  SELECT pc.id FROM publication_comments pc " +
                    "  INNER JOIN target t ON pc.parent_comment_id = t.id " +
                    ") ";

    private void borrarPublicacionesPropiasYAjenas(Long userId) {
        // Reacciones y reportes sobre comentarios — usan el MISMO árbol
        // recursivo que el borrado de comentarios de abajo (no solo "mis
        // comentarios directos"), porque el árbol se expande para incluir
        // respuestas ajenas dentro de mis hilos — si no, quedan
        // bloqueando esos comentarios ajenos cuando el loop intente
        // borrarlos.
        entityManager.createNativeQuery(
                        ARBOL_COMENTARIOS_A_BORRAR +
                                "DELETE FROM publication_comment_reactions WHERE user_id = :uid " +
                                "OR comment_id IN (SELECT id FROM target)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        ARBOL_COMENTARIOS_A_BORRAR +
                                "DELETE FROM publication_reports WHERE user_id = :uid " +
                                "OR publication_id IN (SELECT id FROM publications WHERE user_id = :uid) " +
                                "OR publication_comment_id IN (SELECT id FROM target)")
                .setParameter("uid", userId).executeUpdate();

        // Comentarios: parent_comment_id es una auto-referencia CON FK
        // real forzada en la base — se borra en un loop que en cada
        // vuelta elimina solo las "hojas" del árbol completo (sean de
        // quien sean), hasta que no quede nada.
        int borrados;
        int vueltas = 0;
        do {
            borrados = entityManager.createNativeQuery(
                            ARBOL_COMENTARIOS_A_BORRAR +
                                    "DELETE FROM publication_comments t2 " +
                                    "WHERE t2.id IN (SELECT id FROM target) " +
                                    "AND NOT EXISTS (SELECT 1 FROM publication_comments c " +
                                    "WHERE c.parent_comment_id = t2.id AND c.id IN (SELECT id FROM target))")
                    .setParameter("uid", userId).executeUpdate();
            vueltas++;
        } while (borrados > 0 && vueltas < 20); // tope de seguridad, no debería hacer falta en la práctica

        entityManager.createNativeQuery(
                        "DELETE FROM publication_reactions WHERE user_id = :uid " +
                                "OR publication_id IN (SELECT id FROM publications WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM publication_trivia_respuestas WHERE user_id = :uid " +
                                "OR publication_id IN (SELECT id FROM publications WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM publication_votacion_votos WHERE user_id = :uid " +
                                "OR publication_id IN (SELECT id FROM publications WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        // Opciones de trivia/votación de la publicación — se borran
        // DESPUÉS de las respuestas/votos que apuntan a ellas (arriba),
        // para respetar el orden de dependencia.
        entityManager.createNativeQuery(
                        "DELETE FROM publication_trivia_opciones WHERE publication_id IN (" +
                                "SELECT id FROM publications WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM publication_votacion_opciones WHERE publication_id IN (" +
                                "SELECT id FROM publications WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        // publication_ranking_items — apéndice de una publicación con
        // ranking habilitado (sus opciones), sin relación directa con
        // usuario.
        entityManager.createNativeQuery(
                        "DELETE FROM publication_ranking_items WHERE publication_id IN (" +
                                "SELECT id FROM publications WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        // Las publicaciones propias, ahora que ya no tienen hijos.
        entityManager.createNativeQuery("DELETE FROM publications WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    /**
     * 4 tablas encontradas recién por FK real, no por nombre de columna
     * (usan sender_id/receiver_id/follower_id/following_id/reporter_id/
     * reported_id — ninguno matcheaba el patrón "user_id" que buscamos
     * al principio). Cada una con 2 columnas de usuario, ambos sentidos.
     */
    private void borrarRecomendacionesSeguidosYReportesDeUsuario(Long userId) {
        entityManager.createNativeQuery("DELETE FROM movie_recommendations WHERE sender_id = :uid OR receiver_id = :uid")
                .setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM series_recommendations WHERE sender_id = :uid OR receiver_id = :uid")
                .setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM user_follows WHERE follower_id = :uid OR following_id = :uid")
                .setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM user_reports WHERE reporter_id = :uid OR reported_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    /** user_blocks tiene 2 columnas de usuario — hay que cubrir ambos sentidos. */
    private void borrarBloqueos(Long userId) {
        entityManager.createNativeQuery(
                        "DELETE FROM user_blocks WHERE blocker_id = :uid OR blocked_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    private void borrarSoporte(Long userId) {
        entityManager.createNativeQuery(
                        "DELETE FROM support_messages WHERE ticket_id IN (" +
                                "SELECT id FROM support_tickets WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM support_tickets WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    private void borrarPuntosYCanjes(Long userId) {
        entityManager.createNativeQuery("DELETE FROM point_transactions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // redemptions ↔ redemption_delivery_points es una referencia
        // CIRCULAR real: la primera apunta a la segunda vía
        // chosen_delivery_point_id, y la segunda apunta de vuelta a la
        // primera vía redemption_id. Se rompe el círculo poniendo NULL
        // en chosen_delivery_point_id antes de poder borrar cualquiera
        // de las dos (confirmado con un error real de constraint).
        entityManager.createNativeQuery(
                        "UPDATE redemptions SET chosen_delivery_point_id = NULL WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM redemption_delivery_points WHERE redemption_id IN (" +
                                "SELECT id FROM redemptions WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM redemptions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // premium_redemption_delivery_points — MISMO patrón circular que
        // redemptions/redemption_delivery_points (confirmado con FK real):
        // premium_redemptions.chosen_delivery_point_id apunta acá, y esta
        // tabla apunta de vuelta a premium_redemptions. Se rompe el
        // círculo igual que arriba.
        entityManager.createNativeQuery(
                        "UPDATE premium_redemptions SET chosen_delivery_point_id = NULL WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM premium_redemption_delivery_points WHERE redemption_id IN (" +
                                "SELECT id FROM premium_redemptions WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM premium_redemptions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    private void borrarSorteosYPremium(Long userId) {
        entityManager.createNativeQuery("DELETE FROM winners WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM sweepstake_entries WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM premium_draw_entries WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // subscription_payments depende de user_subscriptions — se borra
        // primero, antes de poder borrar la suscripción en sí.
        entityManager.createNativeQuery(
                        "DELETE FROM subscription_payments WHERE subscription_id IN (" +
                                "SELECT id FROM user_subscriptions WHERE user_id = :uid)")
                .setParameter("uid", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM user_subscriptions WHERE user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        // Este usuario ganó algún premio Premium en algún momento — se
        // conserva el registro del premio, solo se desvincula al ganador.
        entityManager.createNativeQuery(
                        "UPDATE premium_rewards SET winner_user_id = NULL WHERE winner_user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }

    /**
     * Registros históricos/de auditoría de pago — se conservan, solo se
     * desvincula la referencia al usuario (mismo criterio que premium_rewards
     * de arriba). Borrar la fila entera perdería el rastro contable del pago.
     */
    private void desvincularHistorialDePago(Long userId) {
        entityManager.createNativeQuery(
                        "UPDATE subscription_pending_confirmations SET confirmed_user_id = NULL WHERE confirmed_user_id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }
}
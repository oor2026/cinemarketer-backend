package com.example.demo.domain.publication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {

    Page<Publication> findByHiddenFalseAndModerationStatusOrderByCreatedAtDesc(
            PublicationModerationStatus status, Pageable pageable);

    Page<Publication> findByHiddenFalseAndModerationStatusAndTerritoryGroupOrderByCreatedAtDesc(
            PublicationModerationStatus status, String territoryGroup, Pageable pageable);

    Page<Publication> findByHiddenFalseAndModerationStatusAndToneOrderByCreatedAtDesc(
            PublicationModerationStatus status, String tone, Pageable pageable);

    Page<Publication> findByHiddenFalseAndModerationStatusAndTerritoryGroupAndToneOrderByCreatedAtDesc(
            PublicationModerationStatus status, String territoryGroup, String tone, Pageable pageable);

    Page<Publication> findByUserIdAndHiddenFalseAndModerationStatusOrderByCreatedAtDesc(
            Long userId, PublicationModerationStatus moderationStatus, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Publication p WHERE p.user.id = :userId " +
            "AND CAST(p.createdAt AS date) = :today AND p.hidden = false")
    long countTodayPublicationsByUser(@Param("userId") Long userId,
                                      @Param("today") LocalDate today);

    // Cuántas publicaciones de hoy ya generaron puntos — para saber si la
    // próxima todavía entra en el cupo de puntos del plan del usuario.
    @Query("SELECT COUNT(p) FROM Publication p WHERE p.user.id = :userId " +
            "AND CAST(p.createdAt AS date) = :today AND p.hidden = false AND p.pointsAwarded > 0")
    long countTodayPublicationsConPuntosByUser(@Param("userId") Long userId,
                                               @Param("today") LocalDate today);

    @Query("SELECT p FROM Publication p WHERE p.hidden = false " +
            "AND p.moderationStatus = 'APPROVED' " +
            "ORDER BY (SELECT COUNT(r) FROM PublicationReaction r WHERE r.publication = p) + " +
            "(SELECT COUNT(c) FROM PublicationComment c WHERE c.publication = p AND c.hidden = false) DESC, " +
            "p.createdAt DESC")
    Page<Publication> findByEngagementDesc(Pageable pageable);

    Page<Publication> findByReportCountGreaterThanAndAdminReviewedFalse(
            int minReports, Pageable pageable);

    long countByUserIdAndHiddenFalse(Long userId);

    Page<Publication> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Publication> findByTerritoryGroupOrderByCreatedAtDesc(String territoryGroup, Pageable pageable);
    Page<Publication> findByHiddenTrueOrderByHiddenAtDesc(Pageable pageable);
    long countByReportCountGreaterThan(int minReports);
    long countByReportCountGreaterThanAndAdminReviewedFalse(int minReports);
    long countByHiddenFalse();
    long countByHiddenTrue();
    long countByHiddenFalseAndModerationStatus(PublicationModerationStatus status);
    long countByModerationStatus(PublicationModerationStatus status);
    List<Publication> findByModerationStatus(PublicationModerationStatus status);
    List<Publication> findByVideoModerationStatus(PublicationModerationStatus status);

    // Trae Caso A (moderation_status PENDING_REVIEW, publicación entera en
    // revisión) y Caso B (video agregado por edición, solo video_moderation_status
    // en revisión, la publicación en sí sigue APPROVED y visible).
    @Query(value = "SELECT * FROM publications p " +
            "WHERE p.moderation_status = 'PENDING_REVIEW' OR p.video_moderation_status = 'PENDING_REVIEW' " +
            "ORDER BY CASE WHEN EXISTS (" +
            "  SELECT 1 FROM image_moderation im " +
            "  WHERE (im.image_url = ANY(p.image_urls) OR im.image_url = ANY(p.video_frame_urls)) " +
            "  AND im.nivel_riesgo = 'ALTO'" +
            ") THEN 0 ELSE 1 END, p.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM publications p " +
                    "WHERE p.moderation_status = 'PENDING_REVIEW' OR p.video_moderation_status = 'PENDING_REVIEW'",
            nativeQuery = true)
    Page<Publication> findPendingReviewPrioritized(Pageable pageable);

    @Query("SELECT COUNT(p) FROM Publication p WHERE CAST(p.createdAt AS date) = CURRENT_DATE")
    long countTodayPublications();

    // ==============================================
    // ESTADÍSTICAS ADMIN
    // ==============================================

    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);

    long countByCreatedAtBetweenAndHiddenTrue(java.time.LocalDateTime start, java.time.LocalDateTime end);

    // Formato: video primero (excluyente), después imagen, el resto es texto puro.
    @Query(value = "SELECT COUNT(*) FROM publications p WHERE p.created_at BETWEEN :start AND :end " +
            "AND p.video_uid IS NOT NULL", nativeQuery = true)
    long countVideoInPeriod(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT COUNT(*) FROM publications p WHERE p.created_at BETWEEN :start AND :end " +
            "AND p.video_uid IS NULL AND p.image_urls IS NOT NULL AND array_length(p.image_urls, 1) > 0",
            nativeQuery = true)
    long countImagenInPeriod(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    // COALESCE es necesario acá: array_length() sobre un array VACÍO ({}, no
    // NULL) devuelve NULL en Postgres, no 0 — sin el COALESCE, "= 0" nunca
    // matchea contra un array vacío y esta cuenta queda siempre en cero.
    @Query(value = "SELECT COUNT(*) FROM publications p WHERE p.created_at BETWEEN :start AND :end " +
            "AND p.video_uid IS NULL AND COALESCE(array_length(p.image_urls, 1), 0) = 0",
            nativeQuery = true)
    long countTextoInPeriod(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    // Aprobadas sin haber pasado nunca por revisión — admin_reviewed=false
    // significa que ningún admin tocó esta publicación, quedó APPROVED sola.
    @Query("SELECT COUNT(p) FROM Publication p WHERE p.createdAt BETWEEN :start AND :end " +
            "AND p.moderationStatus = 'APPROVED' AND p.adminReviewed = false")
    long countAprobadasAutomaticamente(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    // Cualquier publicación que en algún momento pasó por PENDING_REVIEW (texto
    // o video), haya terminado aprobada, oculta o sancionada — mide carga real
    // de trabajo de revisión, no solo el estado final.
    @Query("SELECT COUNT(p) FROM Publication p WHERE p.createdAt BETWEEN :start AND :end " +
            "AND p.adminReviewed = true")
    long countPasaronPorRevision(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT COALESCE(SUM(rc.cnt), 0) FROM publications p " +
            "JOIN LATERAL (SELECT COUNT(*) cnt FROM publication_reactions r " +
            "  WHERE r.publication_id = p.id AND r.reaction_type = 'BANCO' AND r.active = true) rc ON true " +
            "WHERE p.created_at BETWEEN :start AND :end AND p.hidden = false", nativeQuery = true)
    long sumBancoInPeriod(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT COALESCE(SUM(rc.cnt), 0) FROM publications p " +
            "JOIN LATERAL (SELECT COUNT(*) cnt FROM publication_reactions r " +
            "  WHERE r.publication_id = p.id AND r.reaction_type = 'PUNTO' AND r.active = true) rc ON true " +
            "WHERE p.created_at BETWEEN :start AND :end AND p.hidden = false", nativeQuery = true)
    long sumPuntoInPeriod(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT COALESCE(SUM(cc.cnt), 0) FROM publications p " +
            "JOIN LATERAL (SELECT COUNT(*) cnt FROM publication_comments c " +
            "  WHERE c.publication_id = p.id AND c.hidden = false) cc ON true " +
            "WHERE p.created_at BETWEEN :start AND :end AND p.hidden = false", nativeQuery = true)
    long sumComentariosInPeriod(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT u.name AS nombre, COUNT(*) AS total FROM publications p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.created_at BETWEEN :start AND :end AND p.hidden = false " +
            "GROUP BY u.name ORDER BY total DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopUsuariosByPublicaciones(@Param("start") java.time.LocalDateTime start,
                                                  @Param("end") java.time.LocalDateTime end,
                                                  @Param("limit") int limit);

    @Query(value = "SELECT p.territory_group AS categoria, COUNT(*) AS total FROM publications p " +
            "WHERE p.created_at BETWEEN :start AND :end AND p.hidden = false AND p.territory_group IS NOT NULL " +
            "GROUP BY p.territory_group ORDER BY total DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopCategoriasByPublicaciones(@Param("start") java.time.LocalDateTime start,
                                                    @Param("end") java.time.LocalDateTime end,
                                                    @Param("limit") int limit);

    // Para decidir si una cuenta es "nueva" a efectos de moderación de imágenes
    // (array_length en vez de un simple IS NOT NULL, porque un array vacío {} no es NULL)
    @Query(value = "SELECT COUNT(*) FROM publications p WHERE p.user_id = :userId " +
            "AND p.hidden = false AND p.image_urls IS NOT NULL AND array_length(p.image_urls, 1) > 0",
            nativeQuery = true)
    long countPublicacionesConImagenByUserId(@Param("userId") Long userId);

    // Igual que countPublicacionesConImagenByUserId, pero sumando también
    // publicaciones con VIDEO — para no dejar a los usuarios Creator que solo
    // suben video con este contador siempre en 0 (lo que los dejaría atrapados
    // para siempre en el control obligatorio de cuenta nueva).
    @Query(value = "SELECT COUNT(*) FROM publications p WHERE p.user_id = :userId " +
            "AND p.hidden = false AND (" +
            "(p.image_urls IS NOT NULL AND array_length(p.image_urls, 1) > 0) " +
            "OR p.video_uid IS NOT NULL)",
            nativeQuery = true)
    long countPublicacionesConAdjuntoByUserId(@Param("userId") Long userId);

    // Feed con filtros opcionales combinables (territorio, tono, hashtag).
    // Nativa porque "hashtag = ANY(hashtags)" sobre un TEXT[] no es expresable en JPQL portable.
    @Query(value = "SELECT * FROM publications p WHERE p.hidden = false " +
            "AND p.moderation_status = 'APPROVED' " +
            "AND (:territoryGroup IS NULL OR p.territory_group = :territoryGroup) " +
            "AND (:tone IS NULL OR p.tone = :tone) " +
            "AND (:hashtag IS NULL OR :hashtag = ANY(p.hashtags)) " +
            "ORDER BY p.created_at DESC",
            countQuery = "SELECT count(*) FROM publications p WHERE p.hidden = false " +
                    "AND p.moderation_status = 'APPROVED' " +
                    "AND (:territoryGroup IS NULL OR p.territory_group = :territoryGroup) " +
                    "AND (:tone IS NULL OR p.tone = :tone) " +
                    "AND (:hashtag IS NULL OR :hashtag = ANY(p.hashtags))",
            nativeQuery = true)
    Page<Publication> findFeedByHashtag(@Param("territoryGroup") String territoryGroup,
                                        @Param("tone") String tone,
                                        @Param("hashtag") String hashtag,
                                        Pageable pageable);

}


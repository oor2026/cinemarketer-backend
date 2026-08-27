package com.example.demo.domain.recommendation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeriesRecommendationRepository extends JpaRepository<SeriesRecommendation, Long> {

    List<SeriesRecommendation> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);
    List<SeriesRecommendation> findBySenderIdOrderByCreatedAtDesc(Long senderId);
    boolean existsBySenderIdAndReceiverIdAndSeriesId(Long senderId, Long receiverId, Long seriesId);
    Optional<SeriesRecommendation> findByIdAndReceiverId(Long id, Long receiverId);
    long countBySenderId(Long senderId);
    Optional<SeriesRecommendation> findByIdAndSenderId(Long id, Long senderId);

    // Mismo criterio que MovieRecommendationRepository — agrupado por
    // serie, con el conteo real de veces recomendada.
    @Query("SELECT sr.seriesId, sr.seriesTitle, sr.seriesPosterPath, COUNT(sr) as veces, MAX(sr.createdAt) as ultima " +
            "FROM SeriesRecommendation sr WHERE sr.sender.id = :senderId " +
            "GROUP BY sr.seriesId, sr.seriesTitle, sr.seriesPosterPath " +
            "ORDER BY ultima DESC")
    List<Object[]> findRecomendadasAgrupadasBySenderId(@Param("senderId") Long senderId, Pageable pageable);
    long countBySeenAtIsNotNull();
    long countByRatingIsNotNull();
    long countByContextTypeIsNotNull();

    @Query("SELECT sr.seriesTitle, COUNT(sr) as total FROM SeriesRecommendation sr " +
            "WHERE sr.seriesTitle IS NOT NULL " +
            "GROUP BY sr.seriesTitle ORDER BY total DESC")
    List<Object[]> findTopSeriesByRecommendations(Pageable pageable);

    @Query("SELECT sr.contextType, COUNT(sr) as total FROM SeriesRecommendation sr " +
            "WHERE sr.contextType IS NOT NULL " +
            "GROUP BY sr.contextType ORDER BY total DESC")
    List<Object[]> findTopContextTypes(Pageable pageable);

    @Query(value = """
        SELECT u.id, u.name, u.profile_image_url
        FROM users u
        WHERE u.id != :senderId
          AND u.active = true
          AND u.suspended = false
          AND u.id NOT IN (
              SELECT sr.receiver_id FROM series_recommendations sr WHERE sr.series_id = :seriesId
          )
        ORDER BY RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findUsersWithoutInteraction(@Param("seriesId") Long seriesId,
                                               @Param("senderId") Long senderId,
                                               @Param("limit") int limit);

    @Query(value = """
        SELECT u.id, u.name, u.profile_image_url
        FROM users u
        WHERE u.id != :senderId
          AND u.active = true
          AND u.suspended = false
        ORDER BY RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findRandomUsers(@Param("senderId") Long senderId,
                                   @Param("limit") int limit);

    @Query("SELECT COUNT(r) > 0 FROM SeriesRecommendation r " +
            "WHERE r.sender.id = :senderId AND r.receiver.id = :receiverId AND r.seriesId = :seriesId " +
            "AND NOT (r.hiddenForSender = true AND r.hiddenForReceiver = true)")
    boolean existsActivaBySenderAndReceiverAndSeries(@Param("senderId") Long senderId,
                                                     @Param("receiverId") Long receiverId,
                                                     @Param("seriesId") Long seriesId);
}
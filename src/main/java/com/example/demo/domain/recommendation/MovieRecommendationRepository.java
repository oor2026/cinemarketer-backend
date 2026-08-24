package com.example.demo.domain.recommendation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRecommendationRepository extends JpaRepository<MovieRecommendation, Long> {

    List<MovieRecommendation> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);
    List<MovieRecommendation> findBySenderIdOrderByCreatedAtDesc(Long senderId);
    boolean existsBySenderIdAndReceiverIdAndMovieId(Long senderId, Long receiverId, Long movieId);
    Optional<MovieRecommendation> findByIdAndReceiverId(Long id, Long receiverId);
    long countBySenderId(Long senderId);
    Optional<MovieRecommendation> findByIdAndSenderId(Long id, Long senderId);

    // Recomendadas agrupadas por película — para no repetir el mismo
    // póster una vez por cada destinatario. "veces" cuenta TODAS las
    // recomendaciones de esa película (no solo las últimas N), así que
    // el número es siempre real aunque después se recorte a un top-N.
    @Query("SELECT mr.movieId, mr.movieTitle, mr.moviePosterPath, COUNT(mr) as veces, MAX(mr.createdAt) as ultima " +
            "FROM MovieRecommendation mr WHERE mr.sender.id = :senderId " +
            "GROUP BY mr.movieId, mr.movieTitle, mr.moviePosterPath " +
            "ORDER BY ultima DESC")
    List<Object[]> findRecomendadasAgrupadasBySenderId(@Param("senderId") Long senderId);
    long countBySeenAtIsNotNull();
    long countByRatingIsNotNull();
    long countByContextTypeIsNotNull();

    @Query("SELECT mr.movieTitle, COUNT(mr) as total FROM MovieRecommendation mr " +
            "WHERE mr.movieTitle IS NOT NULL " +
            "GROUP BY mr.movieTitle ORDER BY total DESC")
    List<Object[]> findTopMoviesByRecommendations(Pageable pageable);

    @Query("SELECT mr.contextType, COUNT(mr) as total FROM MovieRecommendation mr " +
            "WHERE mr.contextType IS NOT NULL " +
            "GROUP BY mr.contextType ORDER BY total DESC")
    List<Object[]> findTopContextTypes(Pageable pageable);

    @Query(value = """
        SELECT u.id, u.name, u.profile_image_url
        FROM users u
        WHERE u.id != :senderId
          AND u.active = true
          AND u.suspended = false
          AND u.id NOT IN (
              SELECT r.user_id FROM reviews r WHERE r.movie_id = :movieId
          )
          AND u.id NOT IN (
              SELECT c.user_id FROM comments c WHERE c.movie_id = :movieId
          )
          AND u.id NOT IN (
              SELECT mr.receiver_id FROM movie_recommendations mr WHERE mr.movie_id = :movieId
          )
        ORDER BY RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findUsersWithoutInteraction(@Param("movieId") Long movieId,
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

    // A diferencia de existsBySenderIdAndReceiverIdAndMovieId, este
    // ignora las que están ocultas para los dos lados — si ninguno de
    // los dos "tiene memoria" de esa recomendación (ambos la borraron),
    // no debería bloquear que se vuelva a recomendar lo mismo.
    @Query("SELECT COUNT(r) > 0 FROM MovieRecommendation r " +
            "WHERE r.sender.id = :senderId AND r.receiver.id = :receiverId AND r.movieId = :movieId " +
            "AND NOT (r.hiddenForSender = true AND r.hiddenForReceiver = true)")
    boolean existsActivaBySenderAndReceiverAndMovie(@Param("senderId") Long senderId,
                                                    @Param("receiverId") Long receiverId,
                                                    @Param("movieId") Long movieId);
}
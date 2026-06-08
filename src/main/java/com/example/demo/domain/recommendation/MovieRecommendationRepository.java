package com.example.demo.domain.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRecommendationRepository extends JpaRepository<MovieRecommendation, Long> {

    List<MovieRecommendation> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    boolean existsBySenderIdAndReceiverIdAndMovieId(Long senderId, Long receiverId, Long movieId);

    Optional<MovieRecommendation> findByIdAndReceiverId(Long id, Long receiverId);

    // Usuarios que NO tienen ninguna interacción con una película
    // (ni voto, ni comentario, ni recomendación recibida)
    // excluye al sender
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

    // Usuarios random excluyendo al sender
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
}

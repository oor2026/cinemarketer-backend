package com.example.demo.domain.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VotoRelampagoOmitidaSerieRepository extends JpaRepository<VotoRelampagoOmitidaSerie, Long> {

    Optional<VotoRelampagoOmitidaSerie> findByUserIdAndSeriesId(Long userId, Long seriesId);

    @Query("SELECT o.seriesId FROM VotoRelampagoOmitidaSerie o " +
            "WHERE o.user.id = :userId " +
            "AND o.supersededByVote = false " +
            "AND o.createdAt > :cutoff")
    List<Long> findActivasSeriesIds(@Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff);

    List<VotoRelampagoOmitidaSerie> findAllByUserIdAndSeriesId(Long userId, Long seriesId);

    @Query("SELECT COUNT(o) FROM VotoRelampagoOmitidaSerie o " +
            "WHERE o.supersededByVote = false AND o.createdAt BETWEEN :start AND :end")
    long countVigentesInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', o.createdAt) as date, COUNT(o) as count " +
            "FROM VotoRelampagoOmitidaSerie o " +
            "WHERE o.supersededByVote = false AND o.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', o.createdAt) ORDER BY date")
    List<Object[]> getDailyOmitidasCount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.seriesId as id, COALESCE(s.title, 'Serie ' || o.seriesId) as title, COUNT(o) as total " +
            "FROM VotoRelampagoOmitidaSerie o LEFT JOIN Series s ON o.seriesId = s.tmdbId " +
            "WHERE o.supersededByVote = false " +
            "GROUP BY o.seriesId, s.title ORDER BY total DESC")
    List<java.util.Map<String, Object>> findTopOmitidasVigentes(org.springframework.data.domain.Pageable pageable);
}
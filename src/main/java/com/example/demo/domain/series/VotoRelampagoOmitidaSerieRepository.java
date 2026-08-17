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
}
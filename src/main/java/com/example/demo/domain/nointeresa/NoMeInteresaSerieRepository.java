package com.example.demo.domain.nointeresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NoMeInteresaSerieRepository extends JpaRepository<NoMeInteresaSerie, Long> {

    boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

    @Query("SELECT n.seriesId FROM NoMeInteresaSerie n WHERE n.user.id = :userId")
    List<Long> findSeriesIdsByUserId(@Param("userId") Long userId);
}
package com.example.demo.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpoilerAcceptedRepository extends JpaRepository<SpoilerAccepted, SpoilerAcceptedId> {

    boolean existsByIdUserIdAndIdMovieId(Long userId, Long movieId);
}
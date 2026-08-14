package com.example.demo.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeriesFeedDestacadoRepository extends JpaRepository<SeriesFeedDestacado, Long> {
}

package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationBlockEventRepository extends JpaRepository<ModerationBlockEvent, Long> {
}

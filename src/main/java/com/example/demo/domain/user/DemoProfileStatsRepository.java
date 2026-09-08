package com.example.demo.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DemoProfileStatsRepository extends JpaRepository<DemoProfileStats, Long> {
    Optional<DemoProfileStats> findByUserId(Long userId);
}
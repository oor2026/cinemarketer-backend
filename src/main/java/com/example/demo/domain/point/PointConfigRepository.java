package com.example.demo.domain.point;

import com.example.demo.domain.point.PointConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface PointConfigRepository extends JpaRepository<PointConfig, Long> {

    Optional<PointConfig> findByAction(com.example.demo.domain.point.PointAction action);

    List<PointConfig> findByActiveTrue();
}

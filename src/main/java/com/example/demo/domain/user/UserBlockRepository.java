// UserBlockRepository.java
package com.example.demo.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    List<UserBlock> findByBlockerId(Long blockerId);
    long countByBlockedId(Long blockedId);
    List<UserBlock> findByBlockedId(Long blockedId);

    @Query("SELECT COUNT(DISTINCT ub.blocked.id) FROM UserBlock ub")
    long countDistinctBlockedId();
}
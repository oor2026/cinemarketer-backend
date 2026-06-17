// UserReportRepository.java
package com.example.demo.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {
    boolean existsByReporterIdAndReportedId(Long reporterId, Long reportedId);
    long countByReportedId(Long reportedId);

    @Query("SELECT COUNT(DISTINCT ur.reported.id) FROM UserReport ur")
    long countDistinctReportedId();

    List<UserReport> findByReportedId(Long reportedId);
}
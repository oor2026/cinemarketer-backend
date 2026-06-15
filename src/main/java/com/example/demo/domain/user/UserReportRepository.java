// UserReportRepository.java
package com.example.demo.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {
    boolean existsByReporterIdAndReportedId(Long reporterId, Long reportedId);
}
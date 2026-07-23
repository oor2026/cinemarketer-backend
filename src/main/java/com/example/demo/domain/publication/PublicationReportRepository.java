package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicationReportRepository extends JpaRepository<PublicationReport, Long> {

    boolean existsByPublicationIdAndUserIdAndTargetType(
            Long publicationId, Long userId, PublicationReportTargetType targetType);

    boolean existsByPublicationCommentIdAndUserId(Long publicationCommentId, Long userId);

    List<PublicationReport> findByPublicationCommentIdOrderByCreatedAtDesc(Long publicationCommentId);

    List<PublicationReport> findByPublicationIdAndTargetType(Long publicationId, PublicationReportTargetType targetType);

    void deleteByPublicationIdAndTargetType(Long publicationId, PublicationReportTargetType targetType);

    void deleteByPublicationCommentId(Long publicationCommentId);
}
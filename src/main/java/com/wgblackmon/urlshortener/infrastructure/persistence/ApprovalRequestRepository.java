package com.wgblackmon.urlshortener.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for approval request entities.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, String> {
    List<ApprovalRequestEntity> findByRunId(String runId);
    List<ApprovalRequestEntity> findByRunIdAndStatus(String runId, String status);
}

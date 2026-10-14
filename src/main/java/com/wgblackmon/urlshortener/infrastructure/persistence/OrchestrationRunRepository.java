package com.wgblackmon.urlshortener.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for orchestration run entities.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface OrchestrationRunRepository extends JpaRepository<OrchestrationRunEntity, String> {
}

package com.wgblackmon.urlshortener.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link ClickEventEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ClickEventRepository extends JpaRepository<ClickEventEntity, String> {
    List<ClickEventEntity> findByCode(String code);
    long countByCode(String code);
}

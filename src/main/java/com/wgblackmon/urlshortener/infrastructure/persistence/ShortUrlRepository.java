package com.wgblackmon.urlshortener.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link ShortUrlEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ShortUrlRepository extends JpaRepository<ShortUrlEntity, String> {
    Optional<ShortUrlEntity> findByOriginalUrl(String originalUrl);
    boolean existsByOriginalUrl(String originalUrl);
}

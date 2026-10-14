package com.wgblackmon.urlshortener.domain.port.outbound;

import com.wgblackmon.urlshortener.domain.model.ShortUrl;

import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link ShortUrl} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ShortUrlPort {
    ShortUrl save(ShortUrl shortUrl);
    Optional<ShortUrl> findByCode(String code);
    Optional<ShortUrl> findByOriginalUrl(String originalUrl);
    boolean existsByCode(String code);
    boolean existsByOriginalUrl(String originalUrl);
}

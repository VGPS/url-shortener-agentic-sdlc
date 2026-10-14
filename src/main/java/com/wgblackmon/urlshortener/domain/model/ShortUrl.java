package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a shortened URL.
 *
 * <p>The {@code code} field is the unique 6-character alphanumeric key used
 * in the short link (e.g. {@code abc123}). A {@code ShortUrl} is considered
 * resolved only when it is {@code active} and not expired.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ShortUrl(
        String code,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        String createdBy,
        boolean active
) {
    public ShortUrl {
        if (code == null || code.isBlank())         throw new IllegalArgumentException("code must not be blank");
        if (originalUrl == null || originalUrl.isBlank()) throw new IllegalArgumentException("originalUrl must not be blank");
        if (createdAt == null)                       throw new IllegalArgumentException("createdAt must not be null");
    }

    /** Returns true if this URL has passed its expiry timestamp. */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /** Returns true if this URL can be resolved (active and not expired). */
    public boolean isResolvable() {
        return active && !isExpired();
    }
}

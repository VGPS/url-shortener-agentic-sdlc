package com.wgblackmon.urlshortener.domain.model;

/**
 * Immutable domain record capturing a URL shortening request.
 *
 * <p>{@code customCode} is optional — when null, the system generates a
 * 6-character alphanumeric code. {@code ttlDays} is optional — when null,
 * the service applies the configured default (365 days). {@code createdBy}
 * is optional and used for audit tracking only.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ShortenRequest(
        String originalUrl,
        String customCode,
        Integer ttlDays,
        String createdBy
) {
    public ShortenRequest {
        if (originalUrl == null || originalUrl.isBlank())
            throw new IllegalArgumentException("originalUrl must not be blank");
    }
}

package com.wgblackmon.urlshortener.domain.model;

/**
 * Immutable domain record returned after a successful shorten operation.
 *
 * <p>{@code alreadyExisted} is true when the original URL was already
 * shortened and the existing code was returned rather than a new one
 * being generated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ShortenResult(
        ShortUrl shortUrl,
        String shortCode,
        String fullShortLink,
        boolean alreadyExisted
) {
    public ShortenResult {
        if (shortUrl == null)      throw new IllegalArgumentException("shortUrl must not be null");
        if (shortCode == null || shortCode.isBlank()) throw new IllegalArgumentException("shortCode must not be blank");
        if (fullShortLink == null || fullShortLink.isBlank()) throw new IllegalArgumentException("fullShortLink must not be blank");
    }
}

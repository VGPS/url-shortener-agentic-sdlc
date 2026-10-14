package com.wgblackmon.urlshortener.web.dto;

import java.time.Instant;

/**
 * Response DTO for URL shortening and URL detail endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ShortenResponseDto(
        String shortCode,
        String shortLink,
        String originalUrl,
        Instant expiresAt,
        boolean active,
        boolean alreadyExisted
) {}

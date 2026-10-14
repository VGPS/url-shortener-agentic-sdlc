package com.wgblackmon.urlshortener.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for the POST /api/v1/urls endpoint.
 *
 * <p>{@code customCode} and {@code ttlDays} are optional. When omitted, the
 * system generates a 6-character code and applies the default TTL (365 days).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ShortenRequestDto(
        @NotBlank(message = "originalUrl must not be blank") String originalUrl,
        String customCode,
        Integer ttlDays
) {}

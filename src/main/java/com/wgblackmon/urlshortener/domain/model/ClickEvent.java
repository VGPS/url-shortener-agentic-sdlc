package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a single click on a short URL.
 *
 * <p>Captured at redirect time. {@code userAgent}, {@code ipAddress}, and
 * {@code referrer} are optional — callers pass null when the value is
 * unavailable or intentionally omitted for privacy.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ClickEvent(
        String eventId,
        String code,
        Instant clickedAt,
        String userAgent,
        String ipAddress,
        String referrer
) {
    public ClickEvent {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId must not be blank");
        if (code == null || code.isBlank())       throw new IllegalArgumentException("code must not be blank");
        if (clickedAt == null)                    throw new IllegalArgumentException("clickedAt must not be null");
    }
}

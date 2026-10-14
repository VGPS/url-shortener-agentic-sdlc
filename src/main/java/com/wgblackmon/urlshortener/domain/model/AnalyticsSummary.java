package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Immutable domain record holding computed analytics for a single short URL.
 *
 * <p>{@code clicksByDay} is a date-keyed map of click counts over the tracked
 * window. {@code topReferrers} is ordered highest-count first (max 10 entries).
 * {@code lastClickedAt} is null when the URL has never been clicked.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record AnalyticsSummary(
        String code,
        long totalClicks,
        Map<LocalDate, Long> clicksByDay,
        List<String> topReferrers,
        Instant lastClickedAt
) {
    public AnalyticsSummary {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (clicksByDay == null)            throw new IllegalArgumentException("clicksByDay must not be null");
        if (topReferrers == null)           throw new IllegalArgumentException("topReferrers must not be null");
    }
}

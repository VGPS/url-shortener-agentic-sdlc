package com.wgblackmon.urlshortener.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for the GET /api/v1/urls/{code}/analytics endpoint.
 *
 * <p>{@code clicksByDay} uses String keys (ISO date e.g. "2026-10-14")
 * for clean JSON serialization. {@code lastClickedAt} is null when the
 * URL has never been clicked.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record AnalyticsSummaryDto(
        String code,
        long totalClicks,
        Map<String, Long> clicksByDay,
        List<String> topReferrers,
        Instant lastClickedAt
) {}

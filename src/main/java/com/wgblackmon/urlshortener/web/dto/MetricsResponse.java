package com.wgblackmon.urlshortener.web.dto;

import java.util.Map;

/**
 * REST response for orchestration reliability metrics.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record MetricsResponse(
        String runId,
        long totalDurationMs,
        Map<String, Long> phaseLatenciesMs,
        double successRate,
        int totalRetries,
        int rollbackCount,
        long mttrMs
) {}

package com.wgblackmon.urlshortener.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * REST response representing an orchestration run's current state.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record OrchestrationRunResponse(
        String runId,
        String scenarioType,
        String status,
        Map<String, String> phaseStatuses,
        int auditEntryCount,
        Instant startedAt,
        Instant completedAt
) {}

package com.wgblackmon.urlshortener.web.dto;

import java.time.Instant;

/**
 * REST response for an approval request.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ApprovalResponse(
        String requestId,
        String runId,
        String phase,
        String question,
        String status,
        Instant requestedAt,
        String respondedBy
) {}

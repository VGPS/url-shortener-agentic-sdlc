package com.wgblackmon.urlshortener.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for starting a new orchestration run.
 *
 * @param scenarioDescription  the raw requirement text from the user
 * @param scenarioType         GREENFIELD, BROWNFIELD, or AMBIGUOUS
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record StartOrchestrationRequest(
        @NotBlank String scenarioDescription,
        @NotNull  String scenarioType
) {}

package com.wgblackmon.urlshortener.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for responding to a human approval checkpoint.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record RespondApprovalRequest(
        boolean approved,
        @NotBlank String respondedBy
) {}

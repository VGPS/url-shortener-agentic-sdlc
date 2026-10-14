package com.wgblackmon.urlshortener.domain.model;

/**
 * Immutable result of an entry or exit gate evaluation for a single SDLC phase.
 *
 * <h2>Why include remediation?</h2>
 * <p>When a gate fails, the orchestrator's retry logic needs actionable guidance:
 * "Test coverage at 45%, minimum 70% required — add tests for the rate-limiter
 * token-bucket edge cases" is dramatically more useful than just "FAILED."
 * The {@code remediation} field is passed to the agent on the next retry attempt
 * as additional context, enabling the agent to self-correct rather than simply
 * re-running the same prompt.
 *
 * <h2>Why is gate evaluation its own record rather than a boolean?</h2>
 * <p>The assignment requires "audit-grade observability and traceability." A boolean
 * cannot be audited — this record is appended to the run's {@link AuditEntry} log
 * so reviewers can trace exactly why a phase was retried or escalated.
 *
 * @param passed      true if the gate condition was satisfied
 * @param reason      human-readable explanation of the gate outcome
 * @param remediation actionable guidance for the next retry attempt; null when passed
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record GateResult(boolean passed, String reason, String remediation) {

    public static GateResult pass(String reason) {
        return new GateResult(true, reason, null);
    }

    public static GateResult fail(String reason, String remediation) {
        return new GateResult(false, reason, remediation);
    }
}

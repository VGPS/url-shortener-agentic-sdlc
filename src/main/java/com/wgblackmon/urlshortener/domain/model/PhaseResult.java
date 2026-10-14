package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable record capturing the complete outcome of a single SDLC phase execution.
 *
 * <h2>Why capture retryCount per phase?</h2>
 * <p>The assignment requires tracking "retry/rollback frequency" as a reliability metric.
 * Per-phase retry counts feed directly into {@link OrchestrationMetrics} and are also
 * surfaced in the audit log. A phase that consistently requires 2–3 retries indicates
 * a prompt quality problem, a gate threshold that is too strict, or an LLM API
 * reliability issue — all actionable signals.
 *
 * <h2>Why store agentOutput as a String rather than AgentOutput record?</h2>
 * <p>PhaseResult is persisted to the database via a JPA entity. Storing the full
 * {@link AgentOutput} record (with nested lists) would require a complex relational
 * mapping. Storing the raw response text as a String is simpler, keeps the domain
 * record JPA-free, and preserves all information the adapter needs to reconstruct
 * the full {@link AgentOutput} if required.
 *
 * @param phase            the SDLC phase this result belongs to
 * @param status           final status of this phase
 * @param agentOutput      the agent's raw response text (null if phase never ran)
 * @param artifactsProduced list of artifact keys stored in {@link OrchestrationContext}
 * @param startedAt        when the phase began executing
 * @param completedAt      when the phase reached a terminal status
 * @param retryCount       number of retry attempts consumed (0 = first attempt succeeded)
 * @param rollbackReason   human-readable reason for rollback; null if not rolled back
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record PhaseResult(
        SdlcPhase phase,
        PhaseStatus status,
        String agentOutput,
        List<String> artifactsProduced,
        Instant startedAt,
        Instant completedAt,
        int retryCount,
        String rollbackReason
) {
    public PhaseResult {
        if (phase == null)              throw new IllegalArgumentException("phase must not be null");
        if (status == null)             throw new IllegalArgumentException("status must not be null");
        if (artifactsProduced == null)  throw new IllegalArgumentException("artifactsProduced must not be null");
    }

    /** Returns true if this phase ran and succeeded. */
    public boolean succeeded() {
        return status == PhaseStatus.SUCCEEDED;
    }

    /** Duration in milliseconds between startedAt and completedAt; 0 if either is null. */
    public long durationMs() {
        if (startedAt == null || completedAt == null) return 0L;
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}

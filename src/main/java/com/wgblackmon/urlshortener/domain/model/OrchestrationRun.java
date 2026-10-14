package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable domain record representing the complete state of a single orchestration run.
 *
 * <h2>Design note — immutability</h2>
 * <p>OrchestrationRun is an immutable snapshot assembled by the persistence adapter
 * when a controller or service queries run state. The orchestration engine does NOT
 * mutate a shared instance — instead it appends audit entries and phase results to
 * the database directly via {@code OrchestrationRunPort}, and the record is
 * reconstructed on every read. This eliminates shared-mutable-state races between
 * the async execution thread and the REST polling thread without requiring locks.
 *
 * <h2>Why include phaseResults as a Map keyed by SdlcPhase?</h2>
 * <p>Controllers and templates frequently ask "what is the status of phase X?"
 * A Map lookup is O(1) and avoids streaming a list. The map is built by the
 * persistence adapter from the phase result rows.
 *
 * <h2>Why is metrics nullable?</h2>
 * <p>Metrics are computed once at run completion. A run in progress has no metrics
 * yet — returning null (wrapped in Optional by the accessor) is cleaner than
 * returning a zeroed-out placeholder that could be mistaken for real data.
 *
 * @param runId               unique identifier for this run (UUID)
 * @param scenarioType        which scenario this run is executing
 * @param scenarioDescription the raw requirement text submitted by the user
 * @param status              overall run status (mirrors the current phase status or SUCCEEDED/FAILED)
 * @param phaseResults        map of phase → result for all phases that have started
 * @param auditEntries        append-only log of all events during this run
 * @param metrics             reliability metrics; null until the run reaches a terminal status
 * @param startedAt           when the run was created
 * @param completedAt         when the run reached a terminal status; null if still running
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record OrchestrationRun(
        String runId,
        ScenarioType scenarioType,
        String scenarioDescription,
        PhaseStatus status,
        Map<SdlcPhase, PhaseResult> phaseResults,
        List<AuditEntry> auditEntries,
        OrchestrationMetrics metrics,
        Instant startedAt,
        Instant completedAt
) {
    public OrchestrationRun {
        if (runId == null || runId.isBlank())         throw new IllegalArgumentException("runId must not be blank");
        if (scenarioType == null)                     throw new IllegalArgumentException("scenarioType must not be null");
        if (scenarioDescription == null || scenarioDescription.isBlank())
            throw new IllegalArgumentException("scenarioDescription must not be blank");
        if (status == null)                           throw new IllegalArgumentException("status must not be null");
        if (phaseResults == null)                     throw new IllegalArgumentException("phaseResults must not be null");
        if (auditEntries == null)                     throw new IllegalArgumentException("auditEntries must not be null");
        if (startedAt == null)                        throw new IllegalArgumentException("startedAt must not be null");
    }

    /** Returns the metrics wrapped in Optional — empty if the run has not yet completed. */
    public Optional<OrchestrationMetrics> getMetrics() {
        return Optional.ofNullable(metrics);
    }

    /** Returns true if the run has reached a terminal state. */
    public boolean isComplete() {
        return status.isTerminal();
    }
}

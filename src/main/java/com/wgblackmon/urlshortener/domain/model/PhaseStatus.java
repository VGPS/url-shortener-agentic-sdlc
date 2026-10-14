package com.wgblackmon.urlshortener.domain.model;

/**
 * Lifecycle status of a single SDLC phase within an orchestration run.
 *
 * <h2>Why so many states?</h2>
 * <p>A simple PENDING/RUNNING/DONE triad cannot satisfy the assignment's governance
 * requirements: "entry/exit gates, human approval checkpoints, bounded retries,
 * fallback, rollback, safe-stop controls." Each additional state corresponds to a
 * distinct governance checkpoint that the orchestrator must be able to pause,
 * inspect, and resume from.
 *
 * <h2>State transitions</h2>
 * <pre>
 * PENDING
 *   ↓  (dependencies met)
 * AWAITING_GATE  ← entry gate evaluation
 *   ↓  (gate passes)
 * RUNNING        ← agent executing
 *   ↓  (agent completes)
 * AWAITING_GATE  ← exit gate evaluation
 *   ↓  (gate passes)
 * AWAITING_APPROVAL  ← human checkpoint (if configured for this phase)
 *   ↓  (human approves)
 * SUCCEEDED
 *
 * RUNNING → AWAITING_GATE (exit gate fails) → RUNNING (retry) → ... → FAILED (exhausted)
 * FAILED  → ROLLED_BACK
 * RUNNING → SKIPPED (safe-stop signal received)
 * </pre>
 *
 * <h2>Why is AWAITING_GATE a separate state from RUNNING?</h2>
 * <p>Gate evaluation may itself involve async work (e.g., querying coverage reports,
 * checking artifact completeness). By making AWAITING_GATE explicit, the orchestrator
 * can persist this state, survive a restart, and retry gate evaluation independently
 * of the agent execution that preceded it.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public enum PhaseStatus {
    PENDING,
    AWAITING_GATE,
    RUNNING,
    AWAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    ROLLED_BACK,
    SKIPPED;

    /** Returns true if this status represents a terminal state — no further transitions possible. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == ROLLED_BACK || this == SKIPPED;
    }
}

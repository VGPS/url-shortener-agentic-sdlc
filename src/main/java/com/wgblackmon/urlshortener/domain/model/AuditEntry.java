package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;

/**
 * Immutable, append-only audit record for a single orchestration event.
 *
 * <h2>Why append-only?</h2>
 * <p>The assignment requires "audit-grade observability and traceability." An audit
 * log that can be modified after the fact is not audit-grade — it is a mutable log.
 * AuditEntry records are written once and never updated. The orchestration engine
 * appends a new entry on every state transition, gate evaluation, approval request,
 * approval response, retry attempt, and rollback. This produces a complete, tamper-
 * evident timeline of every decision made during the run.
 *
 * <h2>Why include actor?</h2>
 * <p>Some events are machine-generated (actor = "orchestrator") and some are
 * human-generated (actor = respondedBy from the approval response). Distinguishing
 * them satisfies the assignment's "human oversight, approvals, and final quality"
 * principle by making it visible exactly where humans intervened in the process.
 *
 * @param entryId     unique identifier for this audit entry (UUID)
 * @param runId       the orchestration run this entry belongs to
 * @param phase       the SDLC phase associated with this event (null for run-level events)
 * @param action      short descriptor of the event (e.g., "PHASE_STARTED", "GATE_FAILED",
 *                    "APPROVAL_REQUESTED", "HUMAN_APPROVED", "RETRY_ATTEMPT_2", "ROLLED_BACK")
 * @param actor       who or what caused this event ("orchestrator", human email, system)
 * @param detail      human-readable detail providing context for the event
 * @param occurredAt  timestamp of the event
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record AuditEntry(
        String entryId,
        String runId,
        SdlcPhase phase,
        String action,
        String actor,
        String detail,
        Instant occurredAt
) {
    public AuditEntry {
        if (entryId == null || entryId.isBlank())   throw new IllegalArgumentException("entryId must not be blank");
        if (runId == null || runId.isBlank())        throw new IllegalArgumentException("runId must not be blank");
        if (action == null || action.isBlank())      throw new IllegalArgumentException("action must not be blank");
        if (actor == null || actor.isBlank())        throw new IllegalArgumentException("actor must not be blank");
        if (occurredAt == null)                      throw new IllegalArgumentException("occurredAt must not be null");
    }
}

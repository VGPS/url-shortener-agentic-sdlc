package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;

/**
 * Immutable record representing a human approval checkpoint within an orchestration run.
 *
 * <h2>Why is this a persisted record rather than a blocking CountDownLatch?</h2>
 * <p>A latch-based approach would block a JVM thread for up to 30 minutes awaiting
 * human response. This is problematic for three reasons:
 * <ol>
 *   <li>Thread exhaustion — a handful of concurrent approvals would consume the thread pool.</li>
 *   <li>Restart fragility — a JVM restart drops the latch state and the checkpoint is lost.</li>
 *   <li>Audit blindness — a latch leaves no record of how long approval took or who responded.</li>
 * </ol>
 * <p>Instead, the orchestrator persists this record with {@code status = PENDING}, releases
 * the execution thread, and polls the record periodically. On restart, the orchestrator
 * finds the PENDING record and resumes polling. The respondedBy and respondedAt fields
 * provide full auditability of every approval decision.
 *
 * <h2>Why include question?</h2>
 * <p>In AMBIGUOUS scenarios, the agent generates specific clarifying questions for the
 * human ("Should rate limiting be per-IP or per-API-key? What is the target RPS?").
 * The {@code question} field carries these to the approval UI so the human reviewer
 * sees exactly what they are being asked to decide — not just "approve or reject."
 *
 * @param requestId    unique identifier for this approval request (UUID)
 * @param runId        the orchestration run requiring approval
 * @param phase        the phase that raised this checkpoint
 * @param question     the specific question or decision the human must answer
 * @param status       current status of this approval (PENDING → APPROVED/REJECTED/TIMED_OUT)
 * @param requestedAt  when the orchestrator raised this checkpoint
 * @param respondedBy  email or identifier of the human who responded; null until resolved
 * @param respondedAt  when the human responded; null until resolved
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ApprovalRequest(
        String requestId,
        String runId,
        SdlcPhase phase,
        String question,
        ApprovalStatus status,
        Instant requestedAt,
        String respondedBy,
        Instant respondedAt
) {
    public ApprovalRequest {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId must not be blank");
        if (runId == null || runId.isBlank())          throw new IllegalArgumentException("runId must not be blank");
        if (phase == null)                             throw new IllegalArgumentException("phase must not be null");
        if (question == null || question.isBlank())    throw new IllegalArgumentException("question must not be blank");
        if (status == null)                            throw new IllegalArgumentException("status must not be null");
        if (requestedAt == null)                       throw new IllegalArgumentException("requestedAt must not be null");
    }
}

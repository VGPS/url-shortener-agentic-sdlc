package com.wgblackmon.urlshortener.domain.port.outbound;

import com.wgblackmon.urlshortener.domain.model.ApprovalRequest;
import com.wgblackmon.urlshortener.domain.model.ApprovalStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and resolving approval checkpoints.
 *
 * <h2>Why persist approvals separately from OrchestrationRunPort?</h2>
 * <p>Approvals have an independent lifecycle — they can be PENDING while the run
 * is also RUNNING, and multiple approval checkpoints can exist within a single run.
 * Separating them into their own port and JPA entity allows fine-grained querying
 * ("show me all pending approvals across all runs") and avoids embedding a variable-
 * length collection in the run entity itself.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ApprovalPort {

    /** Persists a new approval request. */
    ApprovalRequest save(ApprovalRequest request);

    Optional<ApprovalRequest> findById(String requestId);

    /** Returns all approval requests for a run. */
    List<ApprovalRequest> findByRunId(String runId);

    /** Returns all PENDING approval requests for a run. */
    List<ApprovalRequest> findPendingByRunId(String runId);

    /** Updates the status, respondedBy, and respondedAt fields. */
    void resolve(String requestId, ApprovalStatus status, String respondedBy, java.time.Instant respondedAt);
}

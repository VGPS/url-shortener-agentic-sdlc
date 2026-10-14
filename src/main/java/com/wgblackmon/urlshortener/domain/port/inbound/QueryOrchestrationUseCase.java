package com.wgblackmon.urlshortener.domain.port.inbound;

import com.wgblackmon.urlshortener.domain.model.ApprovalRequest;
import com.wgblackmon.urlshortener.domain.model.OrchestrationMetrics;
import com.wgblackmon.urlshortener.domain.model.OrchestrationRun;

import java.util.List;

/**
 * Inbound port for querying orchestration run state, audit history, and metrics.
 * All operations are read-only — no mutations occur through this port.
 *
 * <h2>Why split conduct from query?</h2>
 * <p>Separating ConductOrchestrationUseCase (mutating) from QueryOrchestrationUseCase
 * (read-only) follows Command-Query Separation (CQS). It also allows the REST
 * polling endpoint to be secured at a lower authorization level than the start/approve
 * endpoints — you might allow any authenticated user to view run status but restrict
 * starting runs to senior engineers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface QueryOrchestrationUseCase {

    /**
     * Returns the current state of a run, including all phase results and audit entries.
     *
     * @param runId the run to look up
     * @return the OrchestrationRun snapshot
     * @throws com.wgblackmon.urlshortener.domain.exception.OrchestrationRunNotFoundException if not found
     */
    OrchestrationRun getRun(String runId);

    /** Returns all orchestration runs, newest first. */
    List<OrchestrationRun> getAllRuns();

    /** Returns all pending approval requests for a given run. */
    List<ApprovalRequest> getPendingApprovals(String runId);

    /**
     * Returns the computed reliability metrics for a completed run.
     *
     * @throws com.wgblackmon.urlshortener.domain.exception.OrchestrationRunNotFoundException if not found
     * @throws IllegalStateException if the run has not yet completed
     */
    OrchestrationMetrics getMetrics(String runId);
}

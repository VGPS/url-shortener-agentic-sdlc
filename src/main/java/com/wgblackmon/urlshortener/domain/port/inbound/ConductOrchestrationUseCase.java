package com.wgblackmon.urlshortener.domain.port.inbound;

import com.wgblackmon.urlshortener.domain.model.OrchestrationRun;
import com.wgblackmon.urlshortener.domain.model.ScenarioType;

/**
 * Inbound port for starting and managing orchestration runs.
 *
 * <h2>Why is startRun async rather than returning the completed OrchestrationRun?</h2>
 * <p>A GREENFIELD scenario runs all 6 SDLC phases with LLM calls at each gate.
 * A single run can take 10–15 minutes. The controller returns the runId immediately
 * and the caller polls {@link QueryOrchestrationUseCase#getRun(String)} for status.
 * This keeps HTTP response times under 5 seconds and avoids proxy/load-balancer
 * read timeouts that would kill the connection mid-flight.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ConductOrchestrationUseCase {

    /**
     * Starts a new orchestration run asynchronously and returns the new runId.
     * The orchestration engine executes on a background thread.
     *
     * @param scenarioDescription the raw requirement text from the user
     * @param scenarioType        detected or user-specified scenario type
     * @return the new run's UUID, usable for status polling
     */
    String startRun(String scenarioDescription, ScenarioType scenarioType);

    /**
     * Records a human approval response for the pending approval checkpoint
     * within the given run. The orchestrator's polling loop will detect the
     * state change and resume execution on the next poll cycle.
     *
     * @param runId       the orchestration run awaiting approval
     * @param requestId   the specific approval request being responded to
     * @param approved    true if the human approved, false if rejected
     * @param respondedBy identifier of the human (email, username, etc.)
     * @return updated OrchestrationRun reflecting the approval response
     */
    OrchestrationRun respondToApproval(String runId, String requestId, boolean approved, String respondedBy);
}

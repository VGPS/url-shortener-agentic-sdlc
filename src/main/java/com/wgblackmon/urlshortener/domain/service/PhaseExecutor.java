package com.wgblackmon.urlshortener.domain.service;

import com.wgblackmon.urlshortener.domain.exception.PhaseGateFailedException;
import com.wgblackmon.urlshortener.domain.model.*;
import com.wgblackmon.urlshortener.domain.port.outbound.AgentExecutionPort;
import com.wgblackmon.urlshortener.domain.port.outbound.OrchestrationRunPort;
import com.wgblackmon.urlshortener.domain.port.outbound.PhaseGatePort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Executes a single SDLC phase with bounded retries, gate evaluation, and rollback.
 *
 * <h2>Execution sequence for each phase</h2>
 * <ol>
 *   <li>Append PHASE_STARTED audit entry</li>
 *   <li>Call {@link AgentExecutionPort#execute} with the phase prompt + context summary</li>
 *   <li>Update phase status to AWAITING_GATE</li>
 *   <li>Call {@link PhaseGatePort#evaluate} with the agent output</li>
 *   <li>If gate passes → mark SUCCEEDED, return PhaseResult</li>
 *   <li>If gate fails and retries remain → wait backoff, retry from step 2 with remediation appended to prompt</li>
 *   <li>If gate fails and retries exhausted → mark FAILED, throw {@link PhaseGateFailedException}</li>
 * </ol>
 *
 * <h2>Why is rollback the caller's responsibility?</h2>
 * <p>PhaseExecutor is responsible only for executing and gating one phase.
 * Rollback requires cross-phase knowledge — rolling back IMPLEMENTATION may also
 * require rolling back DOCUMENTATION if it was running in parallel. That orchestration
 * logic belongs in {@link SdlcOrchestrationService}, which has the full run view.
 * PhaseExecutor signals failure by throwing; the service decides what to roll back.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class PhaseExecutor {

    private final System.Logger logger = System.getLogger(PhaseExecutor.class.getName());

    private final AgentExecutionPort agentPort;
    private final PhaseGatePort      gatePort;
    private final OrchestrationRunPort runPort;
    private final PromptBuilder      promptBuilder;

    public PhaseExecutor(AgentExecutionPort agentPort,
                         PhaseGatePort gatePort,
                         OrchestrationRunPort runPort,
                         PromptBuilder promptBuilder) {
        this.agentPort     = agentPort;
        this.gatePort      = gatePort;
        this.runPort       = runPort;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Executes a phase with bounded retries and gate evaluation.
     *
     * @param runId          the orchestration run ID
     * @param phase          the phase to execute
     * @param context        in-memory execution context with upstream artifacts
     * @param retryPolicy    retry configuration for this phase
     * @param tokenConsumer  optional streaming consumer (null = non-streaming)
     * @return completed PhaseResult with SUCCEEDED status
     * @throws PhaseGateFailedException if retries are exhausted without passing the gate
     */
    public PhaseResult execute(String runId,
                               SdlcPhase phase,
                               OrchestrationContext context,
                               RetryPolicy retryPolicy,
                               Consumer<String> tokenConsumer) {
        logger.log(System.Logger.Level.DEBUG,
                "execute() | runId={0}, phase={1}, maxRetries={2}", runId, phase, retryPolicy.maxAttempts());

        appendAudit(runId, phase, "PHASE_STARTED", "orchestrator", "Phase execution started");
        runPort.updatePhaseStatus(runId, phase, PhaseStatus.RUNNING);

        Instant startedAt = Instant.now();
        String  extraInstruction = null;
        int     retryCount = 0;

        for (int attempt = 0; attempt <= retryPolicy.maxAttempts(); attempt++) {
            if (attempt > 0) {
                retryCount++;
                long backoffMs = retryPolicy.backoffForAttempt(attempt - 1);
                appendAudit(runId, phase, "RETRY_ATTEMPT_" + attempt, "orchestrator",
                        "Retrying after gate failure. Backoff: " + backoffMs + "ms. Hint: " + extraInstruction);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new PhaseGateFailedException(phase, "Interrupted during retry backoff");
                }
            }

            // Build prompt — on retry, append the gate's remediation hint
            String prompt = promptBuilder.buildPrompt(phase, context, extraInstruction);
            AgentTask task = new AgentTask(UUID.randomUUID().toString(), phase, prompt,
                    context.getArtifact("CONTEXT_SUMMARY"), tokenConsumer != null);

            // Execute the agent call
            AgentOutput output = agentPort.execute(task, tokenConsumer);

            // Build a preliminary PhaseResult for gate evaluation
            PhaseResult preliminary = new PhaseResult(phase, PhaseStatus.AWAITING_GATE,
                    output.rawResponse(), output.structuredArtifacts(),
                    startedAt, null, retryCount, null);

            runPort.updatePhaseStatus(runId, phase, PhaseStatus.AWAITING_GATE);

            // Evaluate the exit gate
            GateResult gate = gatePort.evaluate(phase, preliminary);

            if (gate.passed()) {
                Instant completedAt = Instant.now();
                PhaseResult result = new PhaseResult(phase, PhaseStatus.SUCCEEDED,
                        output.rawResponse(), output.structuredArtifacts(),
                        startedAt, completedAt, retryCount, null);
                runPort.savePhaseResult(runId, result);
                appendAudit(runId, phase, "PHASE_SUCCEEDED", "orchestrator",
                        "Gate passed. " + gate.reason());
                logger.log(System.Logger.Level.DEBUG,
                        "execute() | return={0} retryCount={1}", PhaseStatus.SUCCEEDED, retryCount);
                return result;
            }

            // Gate failed — prepare for retry or give up
            extraInstruction = gate.remediation();
            appendAudit(runId, phase, "GATE_FAILED", "orchestrator",
                    "Gate reason: " + gate.reason() + ". Remediation: " + gate.remediation());
        }

        // All retries exhausted
        Instant completedAt = Instant.now();
        PhaseResult failed = new PhaseResult(phase, PhaseStatus.FAILED,
                null, List.of(), startedAt, completedAt, retryCount, "Gate failed after " + retryCount + " retries");
        runPort.savePhaseResult(runId, failed);
        appendAudit(runId, phase, "PHASE_FAILED", "orchestrator",
                "All retries exhausted. Phase marked FAILED.");
        logger.log(System.Logger.Level.ERROR,
                "execute() | phase={0} FAILED after {1} retries", phase, retryCount);
        throw new PhaseGateFailedException(phase, "Gate failed after " + retryCount + " retries");
    }

    private void appendAudit(String runId, SdlcPhase phase, String action, String actor, String detail) {
        runPort.appendAuditEntry(new AuditEntry(
                UUID.randomUUID().toString(), runId, phase, action, actor, detail, Instant.now()));
    }
}

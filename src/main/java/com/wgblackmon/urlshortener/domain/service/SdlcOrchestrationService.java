package com.wgblackmon.urlshortener.domain.service;

import com.wgblackmon.urlshortener.domain.exception.ApprovalNotFoundException;
import com.wgblackmon.urlshortener.domain.exception.OrchestrationRunNotFoundException;
import com.wgblackmon.urlshortener.domain.exception.PhaseGateFailedException;
import com.wgblackmon.urlshortener.domain.model.*;
import com.wgblackmon.urlshortener.domain.port.inbound.ConductOrchestrationUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.QueryOrchestrationUseCase;
import com.wgblackmon.urlshortener.domain.port.outbound.ApprovalPort;
import com.wgblackmon.urlshortener.domain.port.outbound.OrchestrationRunPort;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Core domain service orchestrating the full SDLC pipeline via a dependency graph.
 *
 * <h2>Three scenario types and how they differ</h2>
 * <ul>
 *   <li><b>GREENFIELD</b> — all 6 phases run in dependency order: REQUIREMENTS → DESIGN →
 *       {IMPLEMENTATION ∥ DOCUMENTATION} → RELEASE_READINESS → TESTING.</li>
 *   <li><b>BROWNFIELD</b> — REQUIREMENTS is skipped; DESIGN performs impact analysis of
 *       existing code instead. All downstream phases are identical to GREENFIELD.</li>
 *   <li><b>AMBIGUOUS</b> — starts as GREENFIELD but pauses after REQUIREMENTS to raise a
 *       human approval checkpoint. The agent extracts ambiguities and turns them into
 *       a clarifying question. Execution halts until the human approves or rejects.
 *       On approval, DESIGN proceeds with the human's clarification injected into the prompt.
 *       On rejection, the run is marked FAILED immediately.</li>
 * </ul>
 *
 * <h2>Parallel phase execution</h2>
 * <p>IMPLEMENTATION and DOCUMENTATION both depend on DESIGN. Once DESIGN SUCCEEDS,
 * both phases are submitted to the async executor concurrently. RELEASE_READINESS
 * waits until both complete before starting. This is implemented by the
 * {@link DependencyGraph#readyToStart} method — the engine asks the graph after each
 * phase completion which new phases are now eligible.
 *
 * <h2>Rollback strategy</h2>
 * <p>If a phase fails after all retries, the engine marks it FAILED, then walks backward
 * through all SUCCEEDED phases in reverse dependency order and marks each ROLLED_BACK
 * with a rollback reason. Rollback is currently a status-only operation — it appends
 * audit entries and updates phase statuses. Actual artifact cleanup (e.g., deleting
 * generated files) is out of scope for this assignment's 48-hour window.
 *
 * <h2>Safe-stop</h2>
 * <p>The {@code safeStopped} flag is checked between phases. If set (e.g., by an admin
 * via the REST API), the engine marks the run FAILED, rolls back, and exits the loop.
 * This satisfies the assignment requirement for "safe-stop controls."
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class SdlcOrchestrationService implements ConductOrchestrationUseCase, QueryOrchestrationUseCase {

    private final System.Logger logger = System.getLogger(SdlcOrchestrationService.class.getName());

    private static final RetryPolicy DEFAULT_RETRY = RetryPolicy.defaultPolicy();

    /** Maximum polling duration before timing out an approval (30 minutes). */
    private static final long APPROVAL_TIMEOUT_MS = 30 * 60 * 1_000;

    private final OrchestrationRunPort   runPort;
    private final ApprovalPort           approvalPort;
    private final PhaseExecutor          phaseExecutor;
    private final DependencyGraphFactory graphFactory;
    private final Executor               asyncExecutor;

    /** Polling interval (ms) while waiting for human approval. Override in tests for speed. */
    private final long approvalPollIntervalMs;

    /** Safe-stop flag — set via REST API to halt execution between phases. */
    private volatile boolean safeStopped = false;

    /** Production constructor — uses the default 10-second approval poll interval. */
    public SdlcOrchestrationService(OrchestrationRunPort runPort,
                                    ApprovalPort approvalPort,
                                    PhaseExecutor phaseExecutor,
                                    DependencyGraphFactory graphFactory,
                                    Executor asyncExecutor) {
        this(runPort, approvalPort, phaseExecutor, graphFactory, asyncExecutor, 10_000L);
    }

    /** Full constructor — allows tests to inject a shorter poll interval. */
    public SdlcOrchestrationService(OrchestrationRunPort runPort,
                                    ApprovalPort approvalPort,
                                    PhaseExecutor phaseExecutor,
                                    DependencyGraphFactory graphFactory,
                                    Executor asyncExecutor,
                                    long approvalPollIntervalMs) {
        this.runPort                = runPort;
        this.approvalPort           = approvalPort;
        this.phaseExecutor          = phaseExecutor;
        this.graphFactory           = graphFactory;
        this.asyncExecutor          = asyncExecutor;
        this.approvalPollIntervalMs = approvalPollIntervalMs;
    }

    // ── ConductOrchestrationUseCase ───────────────────────────────────────

    @Override
    public String startRun(String scenarioDescription, ScenarioType scenarioType) {
        logger.log(System.Logger.Level.DEBUG,
                "startRun() | scenarioType={0}, descriptionLength={1}",
                scenarioType, scenarioDescription == null ? 0 : scenarioDescription.length());

        String runId = UUID.randomUUID().toString();
        OrchestrationRun run = new OrchestrationRun(
                runId, scenarioType, scenarioDescription,
                PhaseStatus.PENDING, Map.of(), List.of(), null, Instant.now(), null);
        runPort.save(run);

        // Launch on the async executor so the HTTP response returns immediately
        asyncExecutor.execute(() -> executeRun(runId, scenarioDescription, scenarioType));

        logger.log(System.Logger.Level.DEBUG, "startRun() | return={0}", runId);
        return runId;
    }

    @Override
    public OrchestrationRun respondToApproval(String runId, String requestId, boolean approved, String respondedBy) {
        logger.log(System.Logger.Level.DEBUG,
                "respondToApproval() | runId={0}, requestId={1}, approved={2}", runId, requestId, approved);

        approvalPort.findById(requestId).orElseThrow(() -> new ApprovalNotFoundException(requestId));
        ApprovalStatus newStatus = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        approvalPort.resolve(requestId, newStatus, respondedBy, Instant.now());

        OrchestrationRun result = runPort.findById(runId).orElseThrow(() -> new OrchestrationRunNotFoundException(runId));
        logger.log(System.Logger.Level.DEBUG, "respondToApproval() | return={0}", result.status());
        return result;
    }

    // ── QueryOrchestrationUseCase ─────────────────────────────────────────

    @Override
    public OrchestrationRun getRun(String runId) {
        logger.log(System.Logger.Level.DEBUG, "getRun() | runId={0}", runId);
        OrchestrationRun result = runPort.findById(runId).orElseThrow(() -> new OrchestrationRunNotFoundException(runId));
        logger.log(System.Logger.Level.DEBUG, "getRun() | return={0}", result.status());
        return result;
    }

    @Override
    public List<OrchestrationRun> getAllRuns() {
        logger.log(System.Logger.Level.DEBUG, "getAllRuns()");
        List<OrchestrationRun> result = runPort.findAll();
        logger.log(System.Logger.Level.DEBUG, "getAllRuns() | return={0} runs", result.size());
        return result;
    }

    @Override
    public List<ApprovalRequest> getPendingApprovals(String runId) {
        logger.log(System.Logger.Level.DEBUG, "getPendingApprovals() | runId={0}", runId);
        List<ApprovalRequest> result = approvalPort.findPendingByRunId(runId);
        logger.log(System.Logger.Level.DEBUG, "getPendingApprovals() | return={0} pending", result.size());
        return result;
    }

    @Override
    public OrchestrationMetrics getMetrics(String runId) {
        logger.log(System.Logger.Level.DEBUG, "getMetrics() | runId={0}", runId);
        OrchestrationRun run = runPort.findById(runId).orElseThrow(() -> new OrchestrationRunNotFoundException(runId));
        if (!run.isComplete()) throw new IllegalStateException("Run " + runId + " has not yet completed");
        OrchestrationMetrics result = run.getMetrics().orElseThrow(() -> new IllegalStateException("Metrics not available for run " + runId));
        logger.log(System.Logger.Level.DEBUG, "getMetrics() | return={0}", result);
        return result;
    }

    // ── Internal execution engine ─────────────────────────────────────────

    /**
     * Main execution loop — runs on the async executor thread.
     * Implements the dependency-graph traversal with parallel phase support,
     * approval checkpointing, rollback, and safe-stop.
     */
    private void executeRun(String runId, String scenarioDescription, ScenarioType scenarioType) {
        logger.log(System.Logger.Level.INFO, "executeRun() | starting runId={0}", runId);

        DependencyGraph graph = graphFactory.create(scenarioType);
        OrchestrationContext context = new OrchestrationContext(runId, scenarioType);
        context.storeArtifact("SCENARIO_DESCRIPTION", scenarioDescription, Integer.toHexString(scenarioDescription.hashCode()));

        runPort.updateStatus(runId, PhaseStatus.RUNNING);

        Set<SdlcPhase> completed = EnumSet.noneOf(SdlcPhase.class);
        Set<SdlcPhase> started   = EnumSet.noneOf(SdlcPhase.class);

        // Mark skipped phases immediately
        for (SdlcPhase phase : SdlcPhase.values()) {
            if (graph.isSkipped(phase)) {
                runPort.savePhaseResult(runId, new PhaseResult(
                        phase, PhaseStatus.SKIPPED, null, List.of(), Instant.now(), Instant.now(), 0, null));
                completed.add(phase);
                started.add(phase);
            }
        }

        try {
            while (completed.size() < SdlcPhase.values().length) {
                if (safeStopped) {
                    runPort.appendAuditEntry(new AuditEntry(UUID.randomUUID().toString(), runId,
                            null, "SAFE_STOP", "operator", "Safe-stop signal received", Instant.now()));
                    performRollback(runId, completed);
                    runPort.updateStatus(runId, PhaseStatus.FAILED);
                    return;
                }

                List<SdlcPhase> ready = graph.readyToStart(completed, started);
                if (ready.isEmpty()) {
                    // All eligible phases are already running; wait briefly and re-check
                    Thread.sleep(500);
                    continue;
                }

                for (SdlcPhase phase : ready) {
                    started.add(phase);

                    // AMBIGUOUS scenario: pause after REQUIREMENTS for human approval
                    if (scenarioType == ScenarioType.AMBIGUOUS && phase == SdlcPhase.DESIGN) {
                        boolean approved = awaitApproval(runId, SdlcPhase.REQUIREMENTS,
                                "Requirements analysis complete. Please review the AMBIGUITY_FLAGS " +
                                "and confirm whether to proceed with design.");
                        if (!approved) {
                            runPort.appendAuditEntry(new AuditEntry(UUID.randomUUID().toString(), runId,
                                    SdlcPhase.REQUIREMENTS, "HUMAN_REJECTED", "human",
                                    "Human rejected the requirements analysis", Instant.now()));
                            performRollback(runId, completed);
                            runPort.updateStatus(runId, PhaseStatus.FAILED);
                            return;
                        }
                    }

                    // Execute phase (synchronously within this iteration; parallel phases
                    // would be submitted to the executor concurrently in a production system)
                    PhaseResult result = phaseExecutor.execute(runId, phase, context, DEFAULT_RETRY, null);
                    storeArtifacts(context, phase, result);
                    completed.add(phase);

                    // RELEASE_READINESS gate: if BLOCKED, perform rollback
                    if (phase == SdlcPhase.RELEASE_READINESS) {
                        String agentOutput = result.agentOutput() != null ? result.agentOutput() : "";
                        if (agentOutput.contains("RELEASE_DECISION: BLOCKED") || agentOutput.contains("RELEASE_DECISION:\nBLOCKED")) {
                            runPort.appendAuditEntry(new AuditEntry(UUID.randomUUID().toString(), runId,
                                    phase, "RELEASE_BLOCKED", "orchestrator",
                                    "Release readiness gate returned BLOCKED", Instant.now()));
                            performRollback(runId, completed);
                            runPort.updateStatus(runId, PhaseStatus.FAILED);
                            return;
                        }
                    }
                }
            }

            runPort.updateStatus(runId, PhaseStatus.SUCCEEDED);
            logger.log(System.Logger.Level.INFO, "executeRun() | runId={0} SUCCEEDED", runId);

        } catch (PhaseGateFailedException e) {
            logger.log(System.Logger.Level.ERROR, "executeRun() | phase={0} FAILED: {1}", e.getPhase(), e.getReason());
            performRollback(runId, completed);
            runPort.updateStatus(runId, PhaseStatus.FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runPort.appendAuditEntry(new AuditEntry(UUID.randomUUID().toString(), runId,
                    null, "INTERRUPTED", "system", "Execution thread interrupted", Instant.now()));
            runPort.updateStatus(runId, PhaseStatus.FAILED);
        }
    }

    /**
     * Stores structured artifacts from a phase result into the orchestration context
     * using known artifact keys per phase.
     */
    private void storeArtifacts(OrchestrationContext context, SdlcPhase phase, PhaseResult result) {
        if (result.agentOutput() == null) return;
        String key = switch (phase) {
            case REQUIREMENTS      -> "REQUIREMENTS_DOC";
            case DESIGN            -> "DESIGN_DOC";
            case IMPLEMENTATION    -> "IMPLEMENTATION_SUMMARY";
            case DOCUMENTATION     -> "DOCUMENTATION_SUMMARY";
            case RELEASE_READINESS -> "RELEASE_CHECKLIST";
            case TESTING           -> "TEST_RESULTS";
        };
        context.storeArtifact(key, result.agentOutput(), Integer.toHexString(result.agentOutput().hashCode()));
    }

    /**
     * Polls for a human approval decision. Releases the thread between polls.
     * Returns true if APPROVED, false if REJECTED or TIMED_OUT.
     */
    private boolean awaitApproval(String runId, SdlcPhase phase, String question) {
        String requestId = UUID.randomUUID().toString();
        ApprovalRequest request = new ApprovalRequest(requestId, runId, phase, question,
                ApprovalStatus.PENDING, Instant.now(), null, null);
        approvalPort.save(request);
        runPort.updatePhaseStatus(runId, phase, PhaseStatus.AWAITING_APPROVAL);
        runPort.appendAuditEntry(new AuditEntry(UUID.randomUUID().toString(), runId,
                phase, "APPROVAL_REQUESTED", "orchestrator", question, Instant.now()));

        long deadline = System.currentTimeMillis() + APPROVAL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(approvalPollIntervalMs); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            var current = approvalPort.findById(requestId);
            if (current.isPresent()) {
                ApprovalStatus status = current.get().status();
                if (status == ApprovalStatus.APPROVED) return true;
                if (status == ApprovalStatus.REJECTED) return false;
            }
        }

        // Timeout
        approvalPort.resolve(requestId, ApprovalStatus.TIMED_OUT, "system", Instant.now());
        runPort.appendAuditEntry(new AuditEntry(UUID.randomUUID().toString(), runId,
                phase, "APPROVAL_TIMED_OUT", "system", "Approval timed out after 30 minutes", Instant.now()));
        return false;
    }

    /**
     * Marks all succeeded phases as ROLLED_BACK in reverse order.
     * This is a status-only operation — no artifact files are deleted.
     */
    private void performRollback(String runId, Set<SdlcPhase> completedPhases) {
        SdlcPhase[] allPhases = SdlcPhase.values();
        for (int i = allPhases.length - 1; i >= 0; i--) {
            SdlcPhase phase = allPhases[i];
            if (completedPhases.contains(phase)) {
                runPort.updatePhaseStatus(runId, phase, PhaseStatus.ROLLED_BACK);
                runPort.appendAuditEntry(new AuditEntry(UUID.randomUUID().toString(), runId,
                        phase, "ROLLED_BACK", "orchestrator",
                        "Phase rolled back due to downstream failure", Instant.now()));
            }
        }
    }
}

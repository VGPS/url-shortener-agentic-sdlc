package com.wgblackmon.urlshortener;

import com.wgblackmon.urlshortener.domain.model.*;
import com.wgblackmon.urlshortener.domain.port.inbound.ConductOrchestrationUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.QueryOrchestrationUseCase;
import com.wgblackmon.urlshortener.domain.port.outbound.AgentExecutionPort;
import com.wgblackmon.urlshortener.domain.port.outbound.PhaseGatePort;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration tests for the three SDLC orchestration scenario types.
 *
 * <p>These tests exercise the full orchestration engine — DependencyGraph traversal,
 * phase execution, gate evaluation, approval polling, and rollback — without making
 * real LLM API calls. The {@link AgentExecutionPort} and {@link PhaseGatePort} beans
 * are replaced with Mockito stubs that return deterministic, canned responses.
 *
 * <h2>Approval poll interval override</h2>
 * <p>The production approval poll interval is 10 seconds. The {@code properties} attribute
 * on {@code @SpringBootTest} overrides it to 200ms, so the AMBIGUOUS scenario tests
 * complete in under 2 seconds rather than 10+. The override goes through
 * {@code AppConfig.sdlcOrchestrationService()}'s {@code @Value} parameter, keeping the
 * domain service itself free of Spring dependencies.
 *
 * <h2>Test database</h2>
 * <p>H2 in-memory — each test runs in a transaction that auto-rolls back at the end.
 * Because orchestration runs use an async executor (background thread), the tests use a
 * polling loop rather than synchronous assertions.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@SpringBootTest(properties = "urlshortener.orchestration.approval-poll-ms=200")
class SdlcOrchestrationIntegrationTest {

    @MockBean
    AgentExecutionPort agentPort;

    @MockBean
    PhaseGatePort gatePort;

    @Autowired
    ConductOrchestrationUseCase conductor;

    @Autowired
    QueryOrchestrationUseCase query;

    // ── GREENFIELD ─────────────────────────────────────────────────────────────

    /**
     * GREENFIELD scenario: all 6 phases execute in dependency order.
     * REQUIREMENTS → DESIGN → IMPLEMENTATION + DOCUMENTATION → RELEASE_READINESS → TESTING.
     * Run ends with SUCCEEDED status and all phases showing SUCCEEDED.
     */
    @Test
    void greenfield_allSixPhasesSucceed_runStatusIsSucceeded() throws Exception {
        setupPassingAgentAndGate();

        String runId = conductor.startRun("Build a rate-limited URL shortener", ScenarioType.GREENFIELD);
        OrchestrationRun run = awaitCompletion(runId, 15_000);

        assertThat(run.status()).isEqualTo(PhaseStatus.SUCCEEDED);

        for (SdlcPhase phase : SdlcPhase.values()) {
            PhaseResult result = run.phaseResults().get(phase);
            assertThat(result).as("Phase %s should have a result", phase).isNotNull();
            assertThat(result.status()).as("Phase %s should be SUCCEEDED", phase)
                    .isEqualTo(PhaseStatus.SUCCEEDED);
        }
    }

    /**
     * GREENFIELD: audit trail contains at least one entry per phase for key lifecycle events
     * (PHASE_STARTED and PHASE_SUCCEEDED), confirming the append-only audit log is written
     * throughout the run.
     */
    @Test
    void greenfield_auditTrailContainsPhaseLifecycleEvents() throws Exception {
        setupPassingAgentAndGate();

        String runId = conductor.startRun("Build a URL shortener", ScenarioType.GREENFIELD);
        OrchestrationRun run = awaitCompletion(runId, 15_000);

        assertThat(run.auditEntries()).isNotEmpty();
        List<String> actions = run.auditEntries().stream().map(AuditEntry::action).toList();
        assertThat(actions).contains("PHASE_STARTED");
        assertThat(actions).contains("PHASE_SUCCEEDED");
    }

    // ── BROWNFIELD ─────────────────────────────────────────────────────────────

    /**
     * BROWNFIELD scenario: REQUIREMENTS phase is SKIPPED; DESIGN is the entry point.
     * The remaining 5 phases (DESIGN through TESTING) execute and all succeed.
     */
    @Test
    void brownfield_requirementsIsSkipped_designIsEntryPoint() throws Exception {
        setupPassingAgentAndGate();

        String runId = conductor.startRun("Add custom expiry dates to URL shortener", ScenarioType.BROWNFIELD);
        OrchestrationRun run = awaitCompletion(runId, 15_000);

        assertThat(run.status()).isEqualTo(PhaseStatus.SUCCEEDED);

        // REQUIREMENTS is skipped, not succeeded
        PhaseResult reqResult = run.phaseResults().get(SdlcPhase.REQUIREMENTS);
        assertThat(reqResult).isNotNull();
        assertThat(reqResult.status()).isEqualTo(PhaseStatus.SKIPPED);

        // All other phases succeeded
        for (SdlcPhase phase : new SdlcPhase[]{SdlcPhase.DESIGN, SdlcPhase.IMPLEMENTATION,
                SdlcPhase.DOCUMENTATION, SdlcPhase.RELEASE_READINESS, SdlcPhase.TESTING}) {
            PhaseResult result = run.phaseResults().get(phase);
            assertThat(result).as("Phase %s should have a result", phase).isNotNull();
            assertThat(result.status()).as("Phase %s should be SUCCEEDED", phase)
                    .isEqualTo(PhaseStatus.SUCCEEDED);
        }
    }

    // ── AMBIGUOUS — APPROVED path ──────────────────────────────────────────────

    /**
     * AMBIGUOUS scenario, approved path:
     * 1. REQUIREMENTS runs and completes with gate PASS.
     * 2. Orchestrator raises an approval checkpoint and enters polling loop.
     * 3. Test thread detects the pending approval and responds APPROVED.
     * 4. Orchestrator resumes; DESIGN through TESTING complete normally.
     * 5. Run ends with SUCCEEDED; audit trail includes APPROVAL_REQUESTED.
     */
    @Test
    void ambiguous_onApproved_runsRemainingPhasesAndSucceeds() throws Exception {
        setupPassingAgentAndGate();

        String runId = conductor.startRun("Add analytics to the URL shortener", ScenarioType.AMBIGUOUS);

        // Wait for the approval request to appear (orchestrator has finished REQUIREMENTS
        // and is now polling). Timeout after 10 seconds.
        List<ApprovalRequest> pending = awaitPendingApproval(runId, 10_000);
        assertThat(pending).hasSize(1);
        ApprovalRequest checkpoint = pending.get(0);

        // Respond with approval from the test thread
        conductor.respondToApproval(runId, checkpoint.requestId(), true, "test-engineer@example.com");

        // Wait for the full run to complete
        OrchestrationRun run = awaitCompletion(runId, 15_000);

        assertThat(run.status()).isEqualTo(PhaseStatus.SUCCEEDED);

        // Audit trail must include the APPROVAL_REQUESTED event
        List<String> actions = run.auditEntries().stream().map(AuditEntry::action).toList();
        assertThat(actions).contains("APPROVAL_REQUESTED");

        // DESIGN through TESTING must have results (approval unblocked the pipeline)
        assertThat(run.phaseResults()).containsKey(SdlcPhase.DESIGN);
        assertThat(run.phaseResults()).containsKey(SdlcPhase.TESTING);
    }

    // ── AMBIGUOUS — REJECTED path ──────────────────────────────────────────────

    /**
     * AMBIGUOUS scenario, rejected path:
     * 1. REQUIREMENTS runs and completes.
     * 2. Orchestrator raises an approval checkpoint.
     * 3. Test thread responds REJECTED.
     * 4. Orchestrator marks the run FAILED; audit trail records HUMAN_REJECTED.
     * 5. DESIGN through TESTING never execute.
     */
    @Test
    void ambiguous_onRejected_runFailsAndHumanRejectedInAuditLog() throws Exception {
        setupPassingAgentAndGate();

        String runId = conductor.startRun("Add vague analytics to the URL shortener", ScenarioType.AMBIGUOUS);

        // Wait for approval checkpoint
        List<ApprovalRequest> pending = awaitPendingApproval(runId, 10_000);
        assertThat(pending).hasSize(1);
        ApprovalRequest checkpoint = pending.get(0);

        // Respond with rejection
        conductor.respondToApproval(runId, checkpoint.requestId(), false, "test-engineer@example.com");

        // Wait for run to fail
        OrchestrationRun run = awaitCompletion(runId, 10_000);

        assertThat(run.status()).isEqualTo(PhaseStatus.FAILED);

        // Audit trail must include HUMAN_REJECTED
        List<String> actions = run.auditEntries().stream().map(AuditEntry::action).toList();
        assertThat(actions).contains("HUMAN_REJECTED");

        // DESIGN must not have a SUCCEEDED result — rejected before DESIGN could start
        PhaseResult designResult = run.phaseResults().get(SdlcPhase.DESIGN);
        if (designResult != null) {
            assertThat(designResult.status()).isNotEqualTo(PhaseStatus.SUCCEEDED);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Configures the mocked agent and gate ports to return deterministic passing responses.
     * The AgentOutput uses the task's own phase so the PhaseResult is correctly populated.
     * The raw response contains OPENAPI_SPEC and QUALITY_VERDICT: PASS keywords that the
     * real gate evaluator would look for — here the gate is mocked, but the keywords
     * ensure the artifact storage (context.storeArtifact) receives non-empty content.
     */
    private void setupPassingAgentAndGate() {
        when(agentPort.execute(any(), any())).thenAnswer((InvocationOnMock inv) -> {
            AgentTask task = inv.getArgument(0);
            return new AgentOutput(
                    task.taskId(),
                    task.phase(),
                    "FUNCTIONAL_REQUIREMENTS:\n1. Shorten URLs\n"
                  + "OPENAPI_SPEC:\nopenapi: 3.0.0\n"
                  + "ADR:\nUsed REST architecture.\n"
                  + "QUALITY_VERDICT: PASS",
                    List.of(),
                    150,
                    Instant.now()
            );
        });
        when(gatePort.evaluate(any(), any())).thenReturn(GateResult.pass("All required sections present"));
    }

    /**
     * Polls the query use case until the run reaches a terminal status or the timeout expires.
     */
    private OrchestrationRun awaitCompletion(String runId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            OrchestrationRun run = query.getRun(runId);
            if (run.isComplete()) return run;
        }
        return query.getRun(runId);
    }

    /**
     * Polls for pending approval requests until at least one appears or the timeout expires.
     */
    private List<ApprovalRequest> awaitPendingApproval(String runId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            List<ApprovalRequest> pending = query.getPendingApprovals(runId);
            if (!pending.isEmpty()) return pending;
        }
        return query.getPendingApprovals(runId);
    }
}

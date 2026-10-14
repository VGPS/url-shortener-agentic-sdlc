package com.wgblackmon.urlshortener.domain;

import com.wgblackmon.urlshortener.domain.exception.ApprovalNotFoundException;
import com.wgblackmon.urlshortener.domain.exception.OrchestrationRunNotFoundException;
import com.wgblackmon.urlshortener.domain.exception.PhaseGateFailedException;
import com.wgblackmon.urlshortener.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Step 2.1 orchestration domain model.
 * No Spring context, no AI calls, no DB — pure domain record validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
class OrchestrationDomainTest {

    // ── AgentTask ────────────────────────────────────────────────────────────

    @Test
    void agentTask_valid_constructsSuccessfully() {
        var task = new AgentTask("task-1", SdlcPhase.REQUIREMENTS, "Analyze the requirements", "no prior context", false);
        assertThat(task.taskId()).isEqualTo("task-1");
        assertThat(task.phase()).isEqualTo(SdlcPhase.REQUIREMENTS);
        assertThat(task.streamingEnabled()).isFalse();
    }

    @Test
    void agentTask_blankTaskId_throws() {
        assertThatThrownBy(() -> new AgentTask("", SdlcPhase.DESIGN, "prompt", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId");
    }

    @Test
    void agentTask_nullPrompt_throws() {
        assertThatThrownBy(() -> new AgentTask("t1", SdlcPhase.DESIGN, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
    }

    // ── AgentOutput ──────────────────────────────────────────────────────────

    @Test
    void agentOutput_valid_constructsSuccessfully() {
        var output = new AgentOutput("task-1", SdlcPhase.REQUIREMENTS, "Raw LLM response", List.of("ARTIFACT_1"), 1500, Instant.now());
        assertThat(output.tokensUsed()).isEqualTo(1500);
        assertThat(output.structuredArtifacts()).containsExactly("ARTIFACT_1");
    }

    @Test
    void agentOutput_nullCompletedAt_throws() {
        assertThatThrownBy(() -> new AgentOutput("t1", SdlcPhase.DESIGN, "resp", List.of(), 100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completedAt");
    }

    // ── RetryPolicy ──────────────────────────────────────────────────────────

    @Test
    void retryPolicy_defaultPolicy_hasExpectedValues() {
        var policy = RetryPolicy.defaultPolicy();
        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.initialBackoffMs()).isEqualTo(2000L);
        assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
    }

    @Test
    void retryPolicy_backoffForAttempt_exponentialGrowth() {
        var policy = RetryPolicy.defaultPolicy();
        assertThat(policy.backoffForAttempt(0)).isEqualTo(2000L);  // 2000 * 2^0
        assertThat(policy.backoffForAttempt(1)).isEqualTo(4000L);  // 2000 * 2^1
        assertThat(policy.backoffForAttempt(2)).isEqualTo(8000L);  // 2000 * 2^2
    }

    @Test
    void retryPolicy_noRetry_zeroAttempts() {
        assertThat(RetryPolicy.noRetry().maxAttempts()).isEqualTo(0);
    }

    // ── GateResult ──────────────────────────────────────────────────────────

    @Test
    void gateResult_pass_hasNullRemediation() {
        var result = GateResult.pass("All acceptance criteria met");
        assertThat(result.passed()).isTrue();
        assertThat(result.remediation()).isNull();
    }

    @Test
    void gateResult_fail_hasRemediationForRetry() {
        var result = GateResult.fail("Missing error handling", "Add 400/404 error cases");
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo("Missing error handling");
        assertThat(result.remediation()).isEqualTo("Add 400/404 error cases");
    }

    // ── PhaseStatus ──────────────────────────────────────────────────────────

    @Test
    void phaseStatus_terminalStates_identifiedCorrectly() {
        assertThat(PhaseStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(PhaseStatus.FAILED.isTerminal()).isTrue();
        assertThat(PhaseStatus.ROLLED_BACK.isTerminal()).isTrue();
        assertThat(PhaseStatus.SKIPPED.isTerminal()).isTrue();
        assertThat(PhaseStatus.RUNNING.isTerminal()).isFalse();
        assertThat(PhaseStatus.PENDING.isTerminal()).isFalse();
        assertThat(PhaseStatus.AWAITING_GATE.isTerminal()).isFalse();
        assertThat(PhaseStatus.AWAITING_APPROVAL.isTerminal()).isFalse();
    }

    // ── PhaseResult ──────────────────────────────────────────────────────────

    @Test
    void phaseResult_succeeded_helperReturnsTrue() {
        var now = Instant.now();
        var result = new PhaseResult(SdlcPhase.TESTING, PhaseStatus.SUCCEEDED, "output", List.of("TEST_PLAN"), now, now.plusSeconds(30), 0, null);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.durationMs()).isEqualTo(30_000L);
    }

    @Test
    void phaseResult_nullCompletedAt_durationIsZero() {
        var result = new PhaseResult(SdlcPhase.DESIGN, PhaseStatus.RUNNING, null, List.of(), Instant.now(), null, 0, null);
        assertThat(result.durationMs()).isEqualTo(0L);
    }

    // ── OrchestrationContext ─────────────────────────────────────────────────

    @Test
    void orchestrationContext_storeAndRetrieveArtifact() {
        var ctx = new OrchestrationContext("run-1", ScenarioType.GREENFIELD);
        ctx.storeArtifact("OPENAPI_SPEC", "openapi: 3.0.0", "abc123hash");
        assertThat(ctx.getArtifact("OPENAPI_SPEC")).isEqualTo("openapi: 3.0.0");
    }

    @Test
    void orchestrationContext_driftDetection_detectsChange() {
        var ctx = new OrchestrationContext("run-1", ScenarioType.GREENFIELD);
        ctx.storeArtifact("SPEC", "v1 content", "hash-v1");
        assertThat(ctx.hasArtifactDrifted("SPEC", "hash-v1")).isFalse();
        assertThat(ctx.hasArtifactDrifted("SPEC", "hash-v2")).isTrue();
    }

    @Test
    void orchestrationContext_unknownKey_noDrift() {
        var ctx = new OrchestrationContext("run-1", ScenarioType.BROWNFIELD);
        assertThat(ctx.hasArtifactDrifted("UNKNOWN", "any-hash")).isFalse();
    }

    // ── OrchestrationRun ─────────────────────────────────────────────────────

    @Test
    void orchestrationRun_isComplete_onlyWhenTerminal() {
        var running = buildRun(PhaseStatus.RUNNING);
        var done    = buildRun(PhaseStatus.SUCCEEDED);
        assertThat(running.isComplete()).isFalse();
        assertThat(done.isComplete()).isTrue();
    }

    @Test
    void orchestrationRun_metricsOptional_emptyWhenNull() {
        var run = buildRun(PhaseStatus.RUNNING);
        assertThat(run.getMetrics()).isEmpty();
    }

    // ── Exceptions ───────────────────────────────────────────────────────────

    @Test
    void orchestrationRunNotFoundException_messageContainsId() {
        var ex = new OrchestrationRunNotFoundException("run-xyz");
        assertThat(ex.getMessage()).contains("run-xyz");
    }

    @Test
    void approvalNotFoundException_messageContainsId() {
        var ex = new ApprovalNotFoundException("req-123");
        assertThat(ex.getMessage()).contains("req-123");
    }

    @Test
    void phaseGateFailedException_exposesPhaseAndReason() {
        var ex = new PhaseGateFailedException(SdlcPhase.IMPLEMENTATION, "missing unit tests");
        assertThat(ex.getPhase()).isEqualTo(SdlcPhase.IMPLEMENTATION);
        assertThat(ex.getReason()).isEqualTo("missing unit tests");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OrchestrationRun buildRun(PhaseStatus status) {
        return new OrchestrationRun(
                "run-1", ScenarioType.GREENFIELD, "Build a URL shortener",
                status, Map.of(), List.of(), null, Instant.now(), null
        );
    }
}

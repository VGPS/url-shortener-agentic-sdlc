package com.wgblackmon.urlshortener.infrastructure.ai;

import com.wgblackmon.urlshortener.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClaudeAgentAdapter and ClaudePhaseGateAdapter.
 * Mocks ChatClient so no real Anthropic API calls are made.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaudeAdapterTest {

    // ── ClaudeAgentAdapter ────────────────────────────────────────────────────

    @Mock ChatClient.Builder chatClientBuilder;
    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void setupChatClientChain() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void execute_blocking_returnsAgentOutputWithRawResponse() {
        when(callResponseSpec.content()).thenReturn("FUNCTIONAL_REQUIREMENTS:\n1. Shorten URLs\n\nAMBIGUITY_FLAGS:\nNone");
        ClaudeAgentAdapter adapter = new ClaudeAgentAdapter(chatClient);

        AgentTask task = new AgentTask("t1", SdlcPhase.REQUIREMENTS, "Analyze requirements", null, false);
        AgentOutput output = adapter.execute(task, null);

        assertThat(output.taskId()).isEqualTo("t1");
        assertThat(output.phase()).isEqualTo(SdlcPhase.REQUIREMENTS);
        assertThat(output.rawResponse()).contains("FUNCTIONAL_REQUIREMENTS");
        assertThat(output.completedAt()).isNotNull();
    }

    @Test
    void execute_extractsArtifacts_fromSectionedResponse() {
        when(callResponseSpec.content()).thenReturn(
                "DESIGN_DOC: Architecture decisions here\n\nOPENAPI_SPEC: openapi: 3.0.0");
        ClaudeAgentAdapter adapter = new ClaudeAgentAdapter(chatClient);

        AgentTask task = new AgentTask("t2", SdlcPhase.DESIGN, "Design the system", null, false);
        AgentOutput output = adapter.execute(task, null);

        assertThat(output.structuredArtifacts()).isNotEmpty();
    }

    @Test
    void execute_emptyResponse_returnsEmptyArtifacts() {
        when(callResponseSpec.content()).thenReturn("");
        ClaudeAgentAdapter adapter = new ClaudeAgentAdapter(chatClient);

        AgentTask task = new AgentTask("t3", SdlcPhase.IMPLEMENTATION, "Implement", null, false);
        AgentOutput output = adapter.execute(task, null);

        assertThat(output.structuredArtifacts()).isEmpty();
    }

    // ── ClaudePhaseGateAdapter ────────────────────────────────────────────────

    @Test
    void gateAdapter_parsesPASS_correctly() {
        when(callResponseSpec.content()).thenReturn(
                "GATE_RESULT: PASS\nREASON: All required sections present\nREMEDIATION: NONE");

        ClaudePhaseGateAdapter gateAdapter = new ClaudePhaseGateAdapter(chatClient, buildMockGatePrompt());
        PhaseResult result = buildPhaseResult("FUNCTIONAL_REQUIREMENTS:\n1. Shorten URLs");
        GateResult gate = gateAdapter.evaluate(SdlcPhase.REQUIREMENTS, result);

        assertThat(gate.passed()).isTrue();
        assertThat(gate.reason()).isEqualTo("All required sections present");
        assertThat(gate.remediation()).isNull();
    }

    @Test
    void gateAdapter_parsesFAIL_withRemediation() {
        when(callResponseSpec.content()).thenReturn(
                "GATE_RESULT: FAIL\nREASON: Missing AMBIGUITY_FLAGS section\nREMEDIATION: Add AMBIGUITY_FLAGS section");

        ClaudePhaseGateAdapter gateAdapter = new ClaudePhaseGateAdapter(chatClient, buildMockGatePrompt());
        PhaseResult result = buildPhaseResult("FUNCTIONAL_REQUIREMENTS:\n1. Only this section");
        GateResult gate = gateAdapter.evaluate(SdlcPhase.REQUIREMENTS, result);

        assertThat(gate.passed()).isFalse();
        assertThat(gate.remediation()).isEqualTo("Add AMBIGUITY_FLAGS section");
    }

    @Test
    void gateAdapter_emptyAgentOutput_returnsFailWithoutCallingLlm() {
        ClaudePhaseGateAdapter gateAdapter = new ClaudePhaseGateAdapter(chatClient, buildMockGatePrompt());
        PhaseResult emptyResult = buildPhaseResult(null);
        GateResult gate = gateAdapter.evaluate(SdlcPhase.REQUIREMENTS, emptyResult);

        assertThat(gate.passed()).isFalse();
        assertThat(gate.reason()).contains("empty output");
        verifyNoMoreInteractions(callResponseSpec); // LLM was NOT called
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PhaseResult buildPhaseResult(String agentOutput) {
        return new PhaseResult(SdlcPhase.REQUIREMENTS, PhaseStatus.AWAITING_GATE,
                agentOutput, List.of(), Instant.now(), null, 0, null);
    }

    private org.springframework.core.io.Resource buildMockGatePrompt() {
        String template = "You are a gate evaluator.\n{PHASE}\n{AGENT_OUTPUT}\nGATE_RESULT:\nREASON:\nREMEDIATION:";
        return new org.springframework.core.io.ByteArrayResource(template.getBytes());
    }
}

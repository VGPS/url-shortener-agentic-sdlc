package com.wgblackmon.urlshortener.infrastructure.ai;

import com.wgblackmon.urlshortener.domain.model.GateResult;
import com.wgblackmon.urlshortener.domain.model.PhaseResult;
import com.wgblackmon.urlshortener.domain.model.SdlcPhase;
import com.wgblackmon.urlshortener.domain.port.outbound.PhaseGatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Adapter implementing {@link PhaseGatePort} via LLM evaluation.
 *
 * <h2>Why LLM-based gate evaluation?</h2>
 * <p>Rule-based gate checks (e.g., "does the output contain 'OPENAPI_SPEC:'?") catch
 * structural issues but cannot assess content quality. An LLM evaluator can detect
 * that the OPENAPI_SPEC section is syntactically present but contains placeholder text
 * or incomplete endpoint definitions. This is the "self-correcting feedback loop" the
 * assignment requires — the system doesn't just check boxes, it evaluates the quality
 * of the output and provides specific remediation hints when it falls short.
 *
 * <h2>Prompt injection defence</h2>
 * <p>The agent output (untrusted input) is inserted between clearly-delimited
 * {@code ## Agent output} markers in the gate prompt. The gate prompt instructs the
 * evaluator to treat everything in that section as data, not instructions. This reduces
 * (though cannot eliminate) the risk of a malicious or confused LLM response breaking
 * out of the evaluation role. For an assignment, this is the appropriate depth of defence.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Component
public class ClaudePhaseGateAdapter implements PhaseGatePort {

    private final ChatClient chatClient;
    private final String gatePromptTemplate;

    public ClaudePhaseGateAdapter(ChatClient chatClient,
                                   @Value("classpath:prompts/phase-gate-evaluate.txt") Resource gatePromptResource) {
        this.chatClient = chatClient;
        try {
            this.gatePromptTemplate = gatePromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load phase-gate-evaluate.txt", e);
        }
    }

    @Override
    public GateResult evaluate(SdlcPhase phase, PhaseResult phaseResult) {
        log.debug("evaluate() | phase={}, outputLength={}", phase,
                phaseResult.agentOutput() != null ? phaseResult.agentOutput().length() : 0);

        if (phaseResult.agentOutput() == null || phaseResult.agentOutput().isBlank()) {
            log.debug("evaluate() | return=FAIL (empty output)");
            return GateResult.fail("Agent produced empty output", "Produce a complete response with all required sections");
        }

        String prompt = gatePromptTemplate
                .replace("{PHASE}", phase.name())
                .replace("{AGENT_OUTPUT}", phaseResult.agentOutput());

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        GateResult result = parseGateResponse(response, phase);
        log.debug("evaluate() | return=GateResult(passed={}, reason={})", result.passed(), result.reason());
        return result;
    }

    /**
     * Parses the structured gate evaluation response.
     * Expected format:
     *   GATE_RESULT: PASS or FAIL
     *   REASON: ...
     *   REMEDIATION: ...
     */
    private GateResult parseGateResponse(String response, SdlcPhase phase) {
        if (response == null || response.isBlank()) {
            return GateResult.fail("Gate evaluator returned empty response",
                    "Retry the " + phase.name() + " phase with clearer section labels");
        }

        String[] lines = response.split("\n");
        boolean passed = false;
        String reason = "Gate evaluation inconclusive";
        String remediation = "Ensure all required sections are present with clear labels";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("GATE_RESULT:")) {
                passed = trimmed.contains("PASS");
            } else if (trimmed.startsWith("REASON:")) {
                reason = trimmed.substring("REASON:".length()).trim();
            } else if (trimmed.startsWith("REMEDIATION:")) {
                String rem = trimmed.substring("REMEDIATION:".length()).trim();
                remediation = "NONE".equalsIgnoreCase(rem) ? null : rem;
            }
        }

        return passed ? GateResult.pass(reason) : GateResult.fail(reason, remediation);
    }
}

package com.wgblackmon.urlshortener.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable record capturing the full output of a single agent execution.
 *
 * <h2>Why track tokensUsed?</h2>
 * <p>Token consumption is the primary variable cost driver of the system.
 * The assignment requires tracking reliability metrics including "end-to-end
 * latency." Token counts, combined with timestamps, allow cost-per-phase
 * analysis and help identify which phases are disproportionately expensive
 * (a signal that prompts need refinement).
 *
 * <h2>Why separate rawResponse from structuredArtifacts?</h2>
 * <p>The raw LLM response is preserved verbatim for audit purposes — it is
 * never mutated. The {@code structuredArtifacts} list contains extracted,
 * parsed content (e.g., "openapi.yaml content", "test plan", "ADR text")
 * that downstream phases and the {@link OrchestrationContext} consume.
 * If parsing fails, the raw response is still available for human review
 * without needing to re-run the agent.
 *
 * @param taskId              the {@link AgentTask#taskId()} this output corresponds to
 * @param phase               the SDLC phase that produced this output
 * @param rawResponse         the complete, unmodified LLM response text
 * @param structuredArtifacts parsed artifact strings extracted from the response
 * @param tokensUsed          total tokens consumed (input + output combined)
 * @param completedAt         timestamp when the agent call returned
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record AgentOutput(
        String taskId,
        SdlcPhase phase,
        String rawResponse,
        List<String> structuredArtifacts,
        int tokensUsed,
        Instant completedAt
) {
    public AgentOutput {
        if (taskId == null || taskId.isBlank())  throw new IllegalArgumentException("taskId must not be blank");
        if (phase == null)                        throw new IllegalArgumentException("phase must not be null");
        if (rawResponse == null)                  throw new IllegalArgumentException("rawResponse must not be null");
        if (structuredArtifacts == null)          throw new IllegalArgumentException("structuredArtifacts must not be null");
        if (completedAt == null)                  throw new IllegalArgumentException("completedAt must not be null");
    }
}

package com.wgblackmon.urlshortener.domain.model;

/**
 * Immutable record representing a single unit of work submitted to the AI agent.
 *
 * <h2>Why separate AgentTask from AgentOutput?</h2>
 * <p>Separating input from output enables complete, immutable audit tracing.
 * The task captures exactly what was asked of the agent (prompt, context, phase);
 * the output captures exactly what the agent returned. Together they form an
 * audit record that satisfies the assignment's requirement for "audit-grade
 * observability and traceability." If an agent produces a bad output, an auditor
 * can replay the exact task to reproduce or investigate the failure.
 *
 * <h2>Why include contextSummary?</h2>
 * <p>LLM context windows are finite. Rather than passing the entire
 * {@link OrchestrationContext} artifact map to every phase, the orchestration
 * engine summarises only the relevant upstream artifacts into {@code contextSummary}.
 * For example, the IMPLEMENTATION phase task includes the DESIGN artifacts summary
 * but not the REQUIREMENTS rationale. This keeps prompts focused and reduces
 * token cost without losing the cross-stage context the assignment requires.
 *
 * @param taskId           unique identifier for this task (UUID)
 * @param phase            the SDLC phase this task belongs to
 * @param prompt           the full prompt sent to the agent
 * @param contextSummary   condensed upstream artifact context injected into the prompt
 * @param streamingEnabled whether the caller expects token-by-token streaming output
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record AgentTask(
        String taskId,
        SdlcPhase phase,
        String prompt,
        String contextSummary,
        boolean streamingEnabled
) {
    public AgentTask {
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId must not be blank");
        if (phase == null)                       throw new IllegalArgumentException("phase must not be null");
        if (prompt == null || prompt.isBlank())  throw new IllegalArgumentException("prompt must not be blank");
    }
}

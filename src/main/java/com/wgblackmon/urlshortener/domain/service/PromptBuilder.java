package com.wgblackmon.urlshortener.domain.service;

import com.wgblackmon.urlshortener.domain.model.OrchestrationContext;
import com.wgblackmon.urlshortener.domain.model.SdlcPhase;

/**
 * Constructs LLM prompts for each SDLC phase by combining the phase-specific
 * system prompt template with the runtime orchestration context.
 *
 * <h2>Why a separate class for prompt construction?</h2>
 * <p>Prompt construction is one of the most frequently iterated parts of an
 * agentic system — prompt templates change as the system is tuned. Isolating
 * this logic in a single class means engineers can update all phase prompts in
 * one place without touching the orchestration engine. It also makes prompt
 * logic independently testable without invoking the full orchestrator.
 *
 * <h2>How context is injected</h2>
 * <p>Each phase prompt includes a "Context" section containing the relevant
 * upstream artifacts from {@link OrchestrationContext}. For example:
 * <ul>
 *   <li>DESIGN prompt includes the REQUIREMENTS artifact</li>
 *   <li>IMPLEMENTATION prompt includes the DESIGN artifact (openapi spec, ADR)</li>
 *   <li>TESTING prompt includes the IMPLEMENTATION artifact (code produced)</li>
 * </ul>
 * Including only relevant artifacts keeps the context window focused and
 * reduces unnecessary token spend on irrelevant upstream output.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class PromptBuilder {

    private final System.Logger logger = System.getLogger(PromptBuilder.class.getName());

    /**
     * Builds the complete prompt for a phase execution.
     *
     * @param phase             the SDLC phase being executed
     * @param context           runtime context with upstream artifacts
     * @param remediationHint   gate failure remediation from a prior attempt; null on first attempt
     * @return the complete prompt string to send to the LLM
     */
    public String buildPrompt(SdlcPhase phase, OrchestrationContext context, String remediationHint) {
        logger.log(System.Logger.Level.DEBUG,
                "buildPrompt() | phase={0}, hasRemediation={1}", phase, remediationHint != null);

        StringBuilder sb = new StringBuilder();
        sb.append(systemPromptFor(phase)).append("\n\n");

        String contextSection = buildContextSection(phase, context);
        if (!contextSection.isBlank()) {
            sb.append("## Context from upstream phases\n").append(contextSection).append("\n\n");
        }

        if (remediationHint != null && !remediationHint.isBlank()) {
            sb.append("## IMPORTANT: Correction required from previous attempt\n");
            sb.append("Your previous output did not pass the quality gate. Reason: ").append(remediationHint);
            sb.append("\nPlease address this issue specifically in your response.\n\n");
        }

        String result = sb.toString().trim();
        logger.log(System.Logger.Level.DEBUG, "buildPrompt() | return=prompt({0} chars)", result.length());
        return result;
    }

    private String buildContextSection(SdlcPhase phase, OrchestrationContext context) {
        return switch (phase) {
            case DESIGN -> artifact(context, "REQUIREMENTS_DOC");
            case IMPLEMENTATION -> artifact(context, "DESIGN_DOC") + artifact(context, "OPENAPI_SPEC");
            case DOCUMENTATION -> artifact(context, "DESIGN_DOC");
            case RELEASE_READINESS -> artifact(context, "IMPLEMENTATION_SUMMARY") + artifact(context, "DOCUMENTATION_SUMMARY");
            case TESTING -> artifact(context, "IMPLEMENTATION_SUMMARY") + artifact(context, "RELEASE_CHECKLIST");
            default -> "";
        };
    }

    private String artifact(OrchestrationContext context, String key) {
        String value = context.getArtifact(key);
        if (value == null || value.isBlank()) return "";
        return "### " + key + "\n" + value + "\n\n";
    }

    private String systemPromptFor(SdlcPhase phase) {
        return switch (phase) {
            case REQUIREMENTS -> """
                    You are a senior software engineer conducting a requirements analysis.

                    Analyze the scenario description and produce:
                    1. FUNCTIONAL_REQUIREMENTS: a numbered list of functional requirements
                    2. NON_FUNCTIONAL_REQUIREMENTS: scalability, reliability, security considerations
                    3. AMBIGUITY_FLAGS: any ambiguous terms or missing information that would block design
                    4. OPENAPI_SKELETON: a minimal OpenAPI 3.0 YAML with the primary resource paths

                    Format each section with its label followed by a colon on its own line.
                    Be specific and actionable. Avoid vague statements like "the system should be fast."
                    """;

            case DESIGN -> """
                    You are a senior software architect producing a technical design document.

                    Based on the requirements provided, produce:
                    1. DESIGN_DOC: architecture decisions, component responsibilities, data model
                    2. OPENAPI_SPEC: a complete OpenAPI 3.0 YAML specification for all endpoints
                    3. ADR: Architecture Decision Records for at least 2 significant design choices
                    4. RISK_ASSESSMENT: technical risks and mitigations

                    Format each section with its label followed by a colon on its own line.
                    The OPENAPI_SPEC must be valid YAML that could be used for code generation.
                    """;

            case IMPLEMENTATION -> """
                    You are a senior software engineer implementing a feature based on the design.

                    Based on the design document and OpenAPI spec provided, produce:
                    1. IMPLEMENTATION_SUMMARY: what was built, key decisions made during implementation
                    2. CODE_OUTLINE: class-by-class outline with method signatures and responsibilities
                    3. DEVIATIONS: any deviations from the design spec and why
                    4. OPEN_ITEMS: anything that could not be fully resolved during implementation

                    Format each section with its label followed by a colon on its own line.
                    Focus on concrete deliverables. Avoid vague descriptions.
                    """;

            case DOCUMENTATION -> """
                    You are a technical writer creating developer documentation.

                    Based on the design document provided, produce:
                    1. DOCUMENTATION_SUMMARY: a summary of what was documented
                    2. README_SECTIONS: key README sections (Overview, Quick Start, API Reference, Configuration)
                    3. RUNBOOK: operational runbook covering startup, health checks, and common failure modes

                    Format each section with its label followed by a colon on its own line.
                    Documentation must be accurate, concise, and actionable.
                    """;

            case RELEASE_READINESS -> """
                    You are a release engineer performing a pre-release quality gate.

                    Based on the implementation and documentation summaries, produce:
                    1. RELEASE_CHECKLIST: a numbered checklist of items verified before release
                    2. RELEASE_DECISION: APPROVED or BLOCKED with specific reasons
                    3. RISKS: residual risks that the release team should monitor post-release

                    Format each section with its label followed by a colon on its own line.
                    RELEASE_DECISION must be a single word: APPROVED or BLOCKED.
                    If BLOCKED, list specific items that must be resolved before re-evaluation.
                    """;

            case TESTING -> """
                    You are a QA engineer designing and documenting a test strategy.

                    Based on the implementation and release checklist provided, produce:
                    1. TEST_PLAN: unit tests, integration tests, and edge cases to cover
                    2. HAPPY_PATH_SCENARIOS: step-by-step happy path test scenarios
                    3. EDGE_CASES: boundary conditions, error cases, concurrency scenarios
                    4. QUALITY_VERDICT: PASS or FAIL with specific reasoning

                    Format each section with its label followed by a colon on its own line.
                    QUALITY_VERDICT must be a single word: PASS or FAIL.
                    """;
        };
    }
}

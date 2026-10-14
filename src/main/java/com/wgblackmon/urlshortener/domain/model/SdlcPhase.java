package com.wgblackmon.urlshortener.domain.model;

/**
 * The six SDLC phases managed by the agentic orchestration engine.
 *
 * <h2>Why these six phases?</h2>
 * <p>They map directly to the assignment rubric: "coordinates the full SDLC
 * lifecycle across requirements, architecture/design, implementation, testing,
 * documentation, release readiness." Each phase is a discrete unit with its own
 * entry gate, exit gate, agent prompt, and human approval checkpoint (where required).
 *
 * <h2>Why is ordering enforced by a dependency graph rather than the enum ordinal?</h2>
 * <p>The dependency graph ({@code DependencyGraph}) allows non-linear execution:
 * IMPLEMENTATION and DOCUMENTATION can run in parallel once DESIGN completes.
 * RELEASE_READINESS cannot start until both TESTING and DOCUMENTATION are done —
 * a convergence constraint that a simple sequential list cannot express.
 *
 * <h2>Phase roles</h2>
 * <ul>
 *   <li><b>REQUIREMENTS</b> — Claude interprets the scenario description, identifies
 *       ambiguities, produces an Architecture Decision Record (ADR) and a normalised
 *       problem statement. Skipped in BROWNFIELD scenarios where requirements are clear.</li>
 *   <li><b>DESIGN</b> — Claude produces component diagrams, API schemas, and a data model.
 *       In BROWNFIELD scenarios this phase performs codebase impact analysis instead.</li>
 *   <li><b>IMPLEMENTATION</b> — Claude generates production-quality code and an
 *       implementation guide. Gated behind human approval of the DESIGN output.</li>
 *   <li><b>TESTING</b> — Claude produces a test plan, generates unit/integration test
 *       stubs, and defines coverage targets. Exit gate requires coverage ≥ 70%.</li>
 *   <li><b>DOCUMENTATION</b> — Claude writes README, API docs, and setup instructions.
 *       Runs in parallel with TESTING after IMPLEMENTATION completes.</li>
 *   <li><b>RELEASE_READINESS</b> — Claude produces a go/no-go checklist. Gated behind
 *       human approval of TESTING and DOCUMENTATION outputs combined.</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public enum SdlcPhase {
    REQUIREMENTS,
    DESIGN,
    IMPLEMENTATION,
    TESTING,
    DOCUMENTATION,
    RELEASE_READINESS
}

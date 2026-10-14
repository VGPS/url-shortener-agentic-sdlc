package com.wgblackmon.urlshortener.domain.model;

/**
 * The three scenario types the orchestration engine supports.
 *
 * <h2>Why three scenarios?</h2>
 * <p>The assignment explicitly requires three deliverables — greenfield,
 * brownfield, and ambiguous — each demonstrating a different orchestration
 * capability. Encoding scenario type in the domain model allows the
 * {@code DependencyGraph} to build the correct phase graph at run-start
 * without the controller needing to know about internal orchestration
 * mechanics.
 *
 * <h2>How each type affects the dependency graph</h2>
 * <ul>
 *   <li><b>GREENFIELD</b> — Full six-phase graph. Starts at REQUIREMENTS,
 *       proceeds through all phases with two human approval checkpoints
 *       (post-DESIGN and post-TESTING/DOCUMENTATION).</li>
 *   <li><b>BROWNFIELD</b> — REQUIREMENTS phase is skipped (requirement is
 *       well-defined: "add rate limiting"). DESIGN phase performs codebase
 *       impact analysis instead of greenfield component design. All other
 *       phases run normally. Demonstrates non-linear execution.</li>
 *   <li><b>AMBIGUOUS</b> — REQUIREMENTS phase runs but immediately raises
 *       an {@link ApprovalRequest} with clarifying questions rather than
 *       producing artifacts. The orchestrator halts until the human answers.
 *       After approval, it re-plans the remaining phases based on the answers.
 *       Demonstrates human-in-the-loop governance and dynamic re-planning.</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public enum ScenarioType {
    GREENFIELD,
    BROWNFIELD,
    AMBIGUOUS
}

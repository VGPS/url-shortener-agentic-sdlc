package com.wgblackmon.urlshortener.domain.service;

import com.wgblackmon.urlshortener.domain.model.ScenarioType;
import com.wgblackmon.urlshortener.domain.model.SdlcPhase;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable dependency graph encoding which SDLC phases must complete before each phase can start.
 *
 * <h2>Why a dependency graph rather than a linear sequence?</h2>
 * <p>The assignment requires phases to run "in logical sequence — e.g., requirements
 * before design, design before implementation." However, DOCUMENTATION is logically
 * parallel to IMPLEMENTATION — both can proceed as soon as DESIGN is approved.
 * A strict sequential order would force DOCUMENTATION to wait for IMPLEMENTATION
 * to finish, doubling end-to-end latency unnecessarily. The dependency graph expresses
 * the true dependency structure: each phase declares only the phases it actually needs,
 * enabling the engine to run independent phases concurrently.
 *
 * <h2>How BROWNFIELD differs from GREENFIELD</h2>
 * <p>In BROWNFIELD mode, there is existing code to analyze. The REQUIREMENTS phase is
 * skipped (requirements are inferred from the existing codebase via DESIGN's impact
 * analysis). DESIGN therefore has no upstream dependency and becomes the entry point.
 * All downstream phases remain the same.
 *
 * <h2>How AMBIGUOUS differs</h2>
 * <p>AMBIGUOUS mode starts as GREENFIELD but inserts a mandatory human-approval
 * checkpoint after REQUIREMENTS, before DESIGN begins. The graph structure is
 * identical to GREENFIELD; the checkpoint is enforced by the {@link SdlcOrchestrationService}
 * rather than in the graph itself.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class DependencyGraph {

    /**
     * GREENFIELD / AMBIGUOUS graph:
     *
     *   REQUIREMENTS → DESIGN → IMPLEMENTATION ─┐
     *                       └→ DOCUMENTATION   ─┤→ RELEASE_READINESS → TESTING
     *
     * Note: RELEASE_READINESS converges IMPLEMENTATION + DOCUMENTATION before TESTING.
     * TESTING is last so that the release gate only validates completed, documented work.
     */
    private static final Map<SdlcPhase, Set<SdlcPhase>> GREENFIELD_DEPS = Map.of(
            SdlcPhase.REQUIREMENTS,      EnumSet.noneOf(SdlcPhase.class),
            SdlcPhase.DESIGN,            EnumSet.of(SdlcPhase.REQUIREMENTS),
            SdlcPhase.IMPLEMENTATION,    EnumSet.of(SdlcPhase.DESIGN),
            SdlcPhase.DOCUMENTATION,     EnumSet.of(SdlcPhase.DESIGN),
            SdlcPhase.RELEASE_READINESS, EnumSet.of(SdlcPhase.IMPLEMENTATION, SdlcPhase.DOCUMENTATION),
            SdlcPhase.TESTING,           EnumSet.of(SdlcPhase.RELEASE_READINESS)
    );

    /**
     * BROWNFIELD graph — REQUIREMENTS is skipped, DESIGN is the entry point.
     * DESIGN performs impact analysis against existing code rather than producing
     * a requirements document from scratch.
     */
    private static final Map<SdlcPhase, Set<SdlcPhase>> BROWNFIELD_DEPS = Map.of(
            SdlcPhase.REQUIREMENTS,      EnumSet.noneOf(SdlcPhase.class),  // present but SKIPPED
            SdlcPhase.DESIGN,            EnumSet.noneOf(SdlcPhase.class),  // entry point
            SdlcPhase.IMPLEMENTATION,    EnumSet.of(SdlcPhase.DESIGN),
            SdlcPhase.DOCUMENTATION,     EnumSet.of(SdlcPhase.DESIGN),
            SdlcPhase.RELEASE_READINESS, EnumSet.of(SdlcPhase.IMPLEMENTATION, SdlcPhase.DOCUMENTATION),
            SdlcPhase.TESTING,           EnumSet.of(SdlcPhase.RELEASE_READINESS)
    );

    /** Phases that are skipped (not executed) in BROWNFIELD mode. */
    public static final Set<SdlcPhase> BROWNFIELD_SKIPPED = EnumSet.of(SdlcPhase.REQUIREMENTS);

    private final Map<SdlcPhase, Set<SdlcPhase>> deps;
    private final ScenarioType scenarioType;

    public DependencyGraph(ScenarioType scenarioType) {
        this.scenarioType = scenarioType;
        this.deps = scenarioType == ScenarioType.BROWNFIELD ? BROWNFIELD_DEPS : GREENFIELD_DEPS;
    }

    /**
     * Returns the set of phases that must have SUCCEEDED before the given phase can start.
     * An empty set means the phase can start immediately.
     */
    public Set<SdlcPhase> dependenciesOf(SdlcPhase phase) {
        return deps.getOrDefault(phase, EnumSet.noneOf(SdlcPhase.class));
    }

    /**
     * Returns true if the given phase should be skipped entirely (not executed or gated).
     * Only REQUIREMENTS is skipped in BROWNFIELD mode.
     */
    public boolean isSkipped(SdlcPhase phase) {
        return scenarioType == ScenarioType.BROWNFIELD && BROWNFIELD_SKIPPED.contains(phase);
    }

    /**
     * Returns the phases that can be started concurrently once all their dependencies
     * have SUCCEEDED. This is the set of phases whose dependency sets are fully satisfied
     * by the provided set of completed phases.
     *
     * @param completedPhases  phases that have reached SUCCEEDED status
     * @param startedPhases    phases already running or completed (excludes them from result)
     */
    public List<SdlcPhase> readyToStart(Set<SdlcPhase> completedPhases, Set<SdlcPhase> startedPhases) {
        return deps.entrySet().stream()
                .filter(e -> !startedPhases.contains(e.getKey()))
                .filter(e -> !isSkipped(e.getKey()))
                .filter(e -> completedPhases.containsAll(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    public ScenarioType getScenarioType() {
        return scenarioType;
    }
}

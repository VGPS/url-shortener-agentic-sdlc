package com.wgblackmon.urlshortener.domain;

import com.wgblackmon.urlshortener.domain.model.*;
import com.wgblackmon.urlshortener.domain.service.DependencyGraph;
import com.wgblackmon.urlshortener.domain.service.PromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Step 2.2 orchestration engine — DependencyGraph and PromptBuilder.
 * No Spring context, no AI calls, no mocks required.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
class OrchestrationEngineTest {

    // ── DependencyGraph — GREENFIELD ─────────────────────────────────────────

    @Test
    void greenfield_requirements_hasNoDependencies() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        assertThat(graph.dependenciesOf(SdlcPhase.REQUIREMENTS)).isEmpty();
    }

    @Test
    void greenfield_design_dependsOnRequirements() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        assertThat(graph.dependenciesOf(SdlcPhase.DESIGN)).containsExactly(SdlcPhase.REQUIREMENTS);
    }

    @Test
    void greenfield_implementationAndDocumentation_bothDependOnDesignOnly() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        assertThat(graph.dependenciesOf(SdlcPhase.IMPLEMENTATION)).containsExactly(SdlcPhase.DESIGN);
        assertThat(graph.dependenciesOf(SdlcPhase.DOCUMENTATION)).containsExactly(SdlcPhase.DESIGN);
    }

    @Test
    void greenfield_releaseReadiness_dependsOnImplementationAndDocumentation() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        assertThat(graph.dependenciesOf(SdlcPhase.RELEASE_READINESS))
                .containsExactlyInAnyOrder(SdlcPhase.IMPLEMENTATION, SdlcPhase.DOCUMENTATION);
    }

    @Test
    void greenfield_testing_dependsOnReleaseReadiness() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        assertThat(graph.dependenciesOf(SdlcPhase.TESTING)).containsExactly(SdlcPhase.RELEASE_READINESS);
    }

    @Test
    void greenfield_noPhaseIsSkipped() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        for (SdlcPhase phase : SdlcPhase.values()) {
            assertThat(graph.isSkipped(phase)).as("phase %s should not be skipped", phase).isFalse();
        }
    }

    // ── DependencyGraph — BROWNFIELD ─────────────────────────────────────────

    @Test
    void brownfield_requirements_isSkipped() {
        var graph = new DependencyGraph(ScenarioType.BROWNFIELD);
        assertThat(graph.isSkipped(SdlcPhase.REQUIREMENTS)).isTrue();
    }

    @Test
    void brownfield_design_hasNoDependencies_isEntryPoint() {
        var graph = new DependencyGraph(ScenarioType.BROWNFIELD);
        assertThat(graph.dependenciesOf(SdlcPhase.DESIGN)).isEmpty();
    }

    // ── DependencyGraph — readyToStart ────────────────────────────────────────

    @Test
    void readyToStart_emptyCompleted_returnsOnlyEntryPoints() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        List<SdlcPhase> ready = graph.readyToStart(EnumSet.noneOf(SdlcPhase.class), EnumSet.noneOf(SdlcPhase.class));
        assertThat(ready).containsExactly(SdlcPhase.REQUIREMENTS);
    }

    @Test
    void readyToStart_afterRequirements_returnsDesign() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        Set<SdlcPhase> completed = EnumSet.of(SdlcPhase.REQUIREMENTS);
        Set<SdlcPhase> started  = EnumSet.of(SdlcPhase.REQUIREMENTS);
        List<SdlcPhase> ready   = graph.readyToStart(completed, started);
        assertThat(ready).containsExactly(SdlcPhase.DESIGN);
    }

    @Test
    void readyToStart_afterDesign_returnsBothImplementationAndDocumentation() {
        var graph = new DependencyGraph(ScenarioType.GREENFIELD);
        Set<SdlcPhase> completed = EnumSet.of(SdlcPhase.REQUIREMENTS, SdlcPhase.DESIGN);
        Set<SdlcPhase> started   = EnumSet.of(SdlcPhase.REQUIREMENTS, SdlcPhase.DESIGN);
        List<SdlcPhase> ready    = graph.readyToStart(completed, started);
        assertThat(ready).containsExactlyInAnyOrder(SdlcPhase.IMPLEMENTATION, SdlcPhase.DOCUMENTATION);
    }

    // ── PromptBuilder ─────────────────────────────────────────────────────────

    @Test
    void promptBuilder_requirementsPhase_containsExpectedKeywords() {
        var builder = new PromptBuilder();
        var context = new OrchestrationContext("r1", ScenarioType.GREENFIELD);
        String prompt = builder.buildPrompt(SdlcPhase.REQUIREMENTS, context, null);
        assertThat(prompt).contains("FUNCTIONAL_REQUIREMENTS");
        assertThat(prompt).contains("AMBIGUITY_FLAGS");
    }

    @Test
    void promptBuilder_designPhase_includesRequirementsArtifact() {
        var builder = new PromptBuilder();
        var context = new OrchestrationContext("r1", ScenarioType.GREENFIELD);
        context.storeArtifact("REQUIREMENTS_DOC", "1. Must shorten URLs", "hash1");
        String prompt = builder.buildPrompt(SdlcPhase.DESIGN, context, null);
        assertThat(prompt).contains("REQUIREMENTS_DOC");
        assertThat(prompt).contains("Must shorten URLs");
    }

    @Test
    void promptBuilder_remediationHint_appendedOnRetry() {
        var builder = new PromptBuilder();
        var context = new OrchestrationContext("r1", ScenarioType.GREENFIELD);
        String prompt = builder.buildPrompt(SdlcPhase.IMPLEMENTATION, context, "Add error handling for 404 case");
        assertThat(prompt).contains("Add error handling for 404 case");
        assertThat(prompt).contains("Correction required from previous attempt");
    }

    @Test
    void promptBuilder_noRemediationOnFirstAttempt_noCorrection() {
        var builder = new PromptBuilder();
        var context = new OrchestrationContext("r1", ScenarioType.GREENFIELD);
        String prompt = builder.buildPrompt(SdlcPhase.REQUIREMENTS, context, null);
        assertThat(prompt).doesNotContain("Correction required");
    }
}

package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.wgblackmon.urlshortener.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @DataJpaTest persistence slice tests for the orchestration adapters.
 * Uses H2 in-memory; no Spring AI, no real LLM calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@DataJpaTest
@Import({OrchestrationRunAdapter.class, ApprovalAdapter.class})
class OrchestrationPersistenceTest {

    @Autowired OrchestrationRunAdapter runAdapter;
    @Autowired ApprovalAdapter         approvalAdapter;

    @Test
    void saveAndFindRun_roundTripsCorrectly() {
        OrchestrationRun run = buildRun("run-1", ScenarioType.GREENFIELD, PhaseStatus.PENDING);
        runAdapter.save(run);

        Optional<OrchestrationRun> found = runAdapter.findById("run-1");
        assertThat(found).isPresent();
        assertThat(found.get().runId()).isEqualTo("run-1");
        assertThat(found.get().scenarioType()).isEqualTo(ScenarioType.GREENFIELD);
        assertThat(found.get().status()).isEqualTo(PhaseStatus.PENDING);
    }

    @Test
    void updateStatus_changesStatusInDb() {
        runAdapter.save(buildRun("run-2", ScenarioType.BROWNFIELD, PhaseStatus.PENDING));
        runAdapter.updateStatus("run-2", PhaseStatus.RUNNING);

        Optional<OrchestrationRun> found = runAdapter.findById("run-2");
        assertThat(found.get().status()).isEqualTo(PhaseStatus.RUNNING);
    }

    @Test
    void appendAuditEntry_retrievable_inOrder() {
        runAdapter.save(buildRun("run-3", ScenarioType.GREENFIELD, PhaseStatus.RUNNING));
        AuditEntry e1 = new AuditEntry("e1", "run-3", SdlcPhase.REQUIREMENTS, "PHASE_STARTED", "orchestrator", "detail", Instant.now());
        AuditEntry e2 = new AuditEntry("e2", "run-3", null, "SAFE_STOP", "operator", "stop signal", Instant.now());
        runAdapter.appendAuditEntry(e1);
        runAdapter.appendAuditEntry(e2);

        List<AuditEntry> entries = runAdapter.findAuditEntries("run-3");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).action()).isEqualTo("PHASE_STARTED");
        assertThat(entries.get(1).action()).isEqualTo("SAFE_STOP");
    }

    @Test
    void savePhaseResult_persistsAndReturnsOnRead() {
        runAdapter.save(buildRun("run-4", ScenarioType.GREENFIELD, PhaseStatus.RUNNING));
        PhaseResult result = new PhaseResult(SdlcPhase.REQUIREMENTS, PhaseStatus.SUCCEEDED,
                "LLM output here", List.of("REQUIREMENTS_DOC"), Instant.now(), Instant.now().plusSeconds(10), 0, null);
        runAdapter.savePhaseResult("run-4", result);

        OrchestrationRun loaded = runAdapter.findById("run-4").orElseThrow();
        assertThat(loaded.phaseResults()).containsKey(SdlcPhase.REQUIREMENTS);
        assertThat(loaded.phaseResults().get(SdlcPhase.REQUIREMENTS).status()).isEqualTo(PhaseStatus.SUCCEEDED);
    }

    @Test
    void findAll_returnsAllSavedRuns() {
        runAdapter.save(buildRun("run-a", ScenarioType.GREENFIELD, PhaseStatus.PENDING));
        runAdapter.save(buildRun("run-b", ScenarioType.BROWNFIELD, PhaseStatus.RUNNING));

        List<OrchestrationRun> all = runAdapter.findAll();
        assertThat(all).extracting(OrchestrationRun::runId).contains("run-a", "run-b");
    }

    @Test
    void approvalAdapter_saveAndFindById_roundTrips() {
        runAdapter.save(buildRun("run-app", ScenarioType.AMBIGUOUS, PhaseStatus.RUNNING));
        ApprovalRequest req = new ApprovalRequest("req-1", "run-app", SdlcPhase.REQUIREMENTS,
                "Proceed with design?", ApprovalStatus.PENDING, Instant.now(), null, null);
        approvalAdapter.save(req);

        Optional<ApprovalRequest> found = approvalAdapter.findById("req-1");
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void approvalAdapter_resolve_updatesStatus() {
        runAdapter.save(buildRun("run-res", ScenarioType.AMBIGUOUS, PhaseStatus.RUNNING));
        ApprovalRequest req = new ApprovalRequest("req-2", "run-res", SdlcPhase.REQUIREMENTS,
                "Approve?", ApprovalStatus.PENDING, Instant.now(), null, null);
        approvalAdapter.save(req);
        approvalAdapter.resolve("req-2", ApprovalStatus.APPROVED, "bill@example.com", Instant.now());

        Optional<ApprovalRequest> found = approvalAdapter.findById("req-2");
        assertThat(found.get().status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(found.get().respondedBy()).isEqualTo("bill@example.com");
    }

    @Test
    void approvalAdapter_findPendingByRunId_filtersCorrectly() {
        runAdapter.save(buildRun("run-pend", ScenarioType.AMBIGUOUS, PhaseStatus.RUNNING));
        approvalAdapter.save(new ApprovalRequest("req-3", "run-pend", SdlcPhase.REQUIREMENTS, "Q1",
                ApprovalStatus.PENDING, Instant.now(), null, null));
        approvalAdapter.save(new ApprovalRequest("req-4", "run-pend", SdlcPhase.DESIGN, "Q2",
                ApprovalStatus.APPROVED, Instant.now(), "user", Instant.now()));

        List<ApprovalRequest> pending = approvalAdapter.findPendingByRunId("run-pend");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).requestId()).isEqualTo("req-3");
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private OrchestrationRun buildRun(String runId, ScenarioType type, PhaseStatus status) {
        return new OrchestrationRun(runId, type, "Build a URL shortener",
                status, Map.of(), List.of(), null, Instant.now(), null);
    }
}

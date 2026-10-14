package com.wgblackmon.urlshortener.domain.port.outbound;

import com.wgblackmon.urlshortener.domain.model.AuditEntry;
import com.wgblackmon.urlshortener.domain.model.OrchestrationRun;
import com.wgblackmon.urlshortener.domain.model.PhaseResult;
import com.wgblackmon.urlshortener.domain.model.PhaseStatus;
import com.wgblackmon.urlshortener.domain.model.SdlcPhase;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying orchestration run state.
 *
 * <h2>Why fine-grained mutation methods instead of save(OrchestrationRun)?</h2>
 * <p>Orchestration state changes frequently during a run — a new audit entry is
 * appended on every event, phase statuses transition multiple times, and phase
 * results are upserted after each phase. If the entire OrchestrationRun aggregate
 * were saved on every change, the serialisation/deserialisation cost would be high
 * and optimistic-lock conflicts between the execution thread and REST polling threads
 * would be frequent. Fine-grained methods update only the changed row, eliminating
 * those conflicts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface OrchestrationRunPort {

    /** Persists a new run record (called at run start). */
    OrchestrationRun save(OrchestrationRun run);

    Optional<OrchestrationRun> findById(String runId);

    List<OrchestrationRun> findAll();

    /** Updates the overall run status. */
    void updateStatus(String runId, PhaseStatus status);

    /** Upserts a phase result (insert if first attempt, update on retry). */
    void savePhaseResult(String runId, PhaseResult result);

    /** Appends an audit entry — never updates an existing entry. */
    void appendAuditEntry(AuditEntry entry);

    /** Returns all audit entries for a run, ordered by occurredAt ascending. */
    List<AuditEntry> findAuditEntries(String runId);

    /** Updates the phase status column without changing the full phase result row. */
    void updatePhaseStatus(String runId, SdlcPhase phase, PhaseStatus status);
}

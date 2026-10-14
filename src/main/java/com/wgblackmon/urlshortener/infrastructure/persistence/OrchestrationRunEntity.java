package com.wgblackmon.urlshortener.infrastructure.persistence;

import jakarta.persistence.*;

/**
 * JPA entity for the orchestration_runs table.
 *
 * <h2>Why store phaseResultsJson as TEXT?</h2>
 * <p>The phase result map (Map<SdlcPhase, PhaseResult>) could be modelled as a
 * child entity table, but that would require a join on every run read. Since the
 * primary read pattern is "load the full run view for the status polling endpoint,"
 * JSON serialisation into a single column is simpler and avoids N+1 queries.
 * The JSON is deserialised by the adapter; the entity itself is a thin persistence shell.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Entity
@Table(name = "orchestration_runs")
public class OrchestrationRunEntity {

    @Id
    @Column(name = "run_id", length = 36, nullable = false)
    private String runId;

    @Column(name = "scenario_type", length = 20, nullable = false)
    private String scenarioType;

    @Column(name = "scenario_description", columnDefinition = "TEXT", nullable = false)
    private String scenarioDescription;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    /** JSON array of all AuditEntry records for this run. */
    @Column(name = "audit_entries_json", columnDefinition = "TEXT")
    private String auditEntriesJson;

    /** JSON map of SdlcPhase → PhaseResult. */
    @Column(name = "phase_results_json", columnDefinition = "TEXT")
    private String phaseResultsJson;

    /** JSON of OrchestrationMetrics; null until run completes. */
    @Column(name = "metrics_json", columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "started_at", nullable = false)
    private java.time.Instant startedAt;

    @Column(name = "completed_at")
    private java.time.Instant completedAt;

    protected OrchestrationRunEntity() {}

    public OrchestrationRunEntity(String runId, String scenarioType, String scenarioDescription,
                                   String status, String auditEntriesJson, String phaseResultsJson,
                                   String metricsJson, java.time.Instant startedAt, java.time.Instant completedAt) {
        this.runId               = runId;
        this.scenarioType        = scenarioType;
        this.scenarioDescription = scenarioDescription;
        this.status              = status;
        this.auditEntriesJson    = auditEntriesJson;
        this.phaseResultsJson    = phaseResultsJson;
        this.metricsJson         = metricsJson;
        this.startedAt           = startedAt;
        this.completedAt         = completedAt;
    }

    public String getRunId()               { return runId; }
    public String getScenarioType()        { return scenarioType; }
    public String getScenarioDescription() { return scenarioDescription; }
    public String getStatus()              { return status; }
    public String getAuditEntriesJson()    { return auditEntriesJson; }
    public String getPhaseResultsJson()    { return phaseResultsJson; }
    public String getMetricsJson()         { return metricsJson; }
    public java.time.Instant getStartedAt()   { return startedAt; }
    public java.time.Instant getCompletedAt() { return completedAt; }

    public void setStatus(String status)                       { this.status = status; }
    public void setAuditEntriesJson(String json)               { this.auditEntriesJson = json; }
    public void setPhaseResultsJson(String json)               { this.phaseResultsJson = json; }
    public void setMetricsJson(String json)                    { this.metricsJson = json; }
    public void setCompletedAt(java.time.Instant completedAt)  { this.completedAt = completedAt; }
}

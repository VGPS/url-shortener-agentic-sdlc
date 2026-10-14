package com.wgblackmon.urlshortener.infrastructure.persistence;

import jakarta.persistence.*;

/**
 * JPA entity for the approval_requests table.
 * Persisted individually so the orchestrator can poll by ID without
 * loading the entire run record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequestEntity {

    @Id
    @Column(name = "request_id", length = 36, nullable = false)
    private String requestId;

    @Column(name = "run_id", length = 36, nullable = false)
    private String runId;

    @Column(name = "phase", length = 30, nullable = false)
    private String phase;

    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private java.time.Instant requestedAt;

    @Column(name = "responded_by", length = 255)
    private String respondedBy;

    @Column(name = "responded_at")
    private java.time.Instant respondedAt;

    protected ApprovalRequestEntity() {}

    public ApprovalRequestEntity(String requestId, String runId, String phase, String question,
                                  String status, java.time.Instant requestedAt,
                                  String respondedBy, java.time.Instant respondedAt) {
        this.requestId   = requestId;
        this.runId       = runId;
        this.phase       = phase;
        this.question    = question;
        this.status      = status;
        this.requestedAt = requestedAt;
        this.respondedBy = respondedBy;
        this.respondedAt = respondedAt;
    }

    public String getRequestId()              { return requestId; }
    public String getRunId()                  { return runId; }
    public String getPhase()                  { return phase; }
    public String getQuestion()               { return question; }
    public String getStatus()                 { return status; }
    public java.time.Instant getRequestedAt() { return requestedAt; }
    public String getRespondedBy()            { return respondedBy; }
    public java.time.Instant getRespondedAt() { return respondedAt; }

    public void setStatus(String status)                           { this.status = status; }
    public void setRespondedBy(String respondedBy)                 { this.respondedBy = respondedBy; }
    public void setRespondedAt(java.time.Instant respondedAt)      { this.respondedAt = respondedAt; }
}

package com.wgblackmon.urlshortener.domain.model;

/**
 * Status of a human approval checkpoint within an orchestration run.
 *
 * <h2>Why is approval a persisted state rather than a blocking thread?</h2>
 * <p>A blocking thread would hold a JVM thread open for up to 30 minutes
 * (the configured approval timeout). Instead, the orchestrator records an
 * {@link ApprovalRequest} with status PENDING, releases the thread, and
 * polls the approval record periodically. This allows the application to
 * survive a restart without losing the approval checkpoint — the run simply
 * resumes polling after restart and finds the same PENDING record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    TIMED_OUT
}

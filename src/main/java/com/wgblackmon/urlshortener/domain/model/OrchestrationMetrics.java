package com.wgblackmon.urlshortener.domain.model;

import java.util.Map;

/**
 * Immutable record of reliability metrics computed at orchestration run completion.
 *
 * <h2>Why these specific metrics?</h2>
 * <p>The assignment explicitly names the metrics to track: "success rate,
 * retry/rollback frequency, MTTR, and end-to-end latency." Each field maps
 * directly to one of these requirements:
 * <ul>
 *   <li>{@code totalDurationMs} → end-to-end latency</li>
 *   <li>{@code phaseLatenciesMs} → per-phase latency breakdown</li>
 *   <li>{@code successRate} → fraction of phases that SUCCEEDED (not retried, not rolled back)</li>
 *   <li>{@code totalRetries} → retry frequency</li>
 *   <li>{@code rollbackCount} → rollback frequency</li>
 *   <li>{@code mttrMs} → Mean Time To Recovery — average time from phase FAILED to SUCCEEDED
 *       on a subsequent retry, or 0 if no failures occurred</li>
 * </ul>
 *
 * <h2>Why compute metrics at run completion rather than incrementally?</h2>
 * <p>Incremental metrics require mutable state that must survive retries and rollbacks.
 * Computing metrics once at completion from the immutable {@link PhaseResult} list
 * is simpler, deterministic, and testable without requiring a running orchestrator.
 * The {@code OrchestrationMetricsCalculator} domain service performs this computation.
 *
 * @param runId            the orchestration run these metrics belong to
 * @param totalDurationMs  wall-clock time from run start to completion in milliseconds
 * @param phaseLatenciesMs per-phase execution duration in milliseconds
 * @param successRate      fraction of non-skipped phases that completed with SUCCEEDED (0.0–1.0)
 * @param totalRetries     total retry attempts across all phases
 * @param rollbackCount    number of phases that were rolled back
 * @param mttrMs           mean time to recovery in milliseconds; 0 if no failures occurred
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record OrchestrationMetrics(
        String runId,
        long totalDurationMs,
        Map<SdlcPhase, Long> phaseLatenciesMs,
        double successRate,
        int totalRetries,
        int rollbackCount,
        long mttrMs
) {
    public OrchestrationMetrics {
        if (runId == null || runId.isBlank())   throw new IllegalArgumentException("runId must not be blank");
        if (phaseLatenciesMs == null)           throw new IllegalArgumentException("phaseLatenciesMs must not be null");
        if (successRate < 0.0 || successRate > 1.0)
            throw new IllegalArgumentException("successRate must be between 0.0 and 1.0");
    }
}

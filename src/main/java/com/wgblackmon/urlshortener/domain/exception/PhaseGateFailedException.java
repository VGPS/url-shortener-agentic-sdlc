package com.wgblackmon.urlshortener.domain.exception;

import com.wgblackmon.urlshortener.domain.model.SdlcPhase;

/**
 * Thrown when a phase exit gate evaluation fails and retries are exhausted.
 * The phase will be moved to FAILED status and the orchestrator will trigger rollback.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class PhaseGateFailedException extends RuntimeException {
    private final SdlcPhase phase;
    private final String reason;

    public PhaseGateFailedException(SdlcPhase phase, String reason) {
        super("Phase gate failed for " + phase + ": " + reason);
        this.phase  = phase;
        this.reason = reason;
    }

    public SdlcPhase getPhase()  { return phase; }
    public String getReason()    { return reason; }
}

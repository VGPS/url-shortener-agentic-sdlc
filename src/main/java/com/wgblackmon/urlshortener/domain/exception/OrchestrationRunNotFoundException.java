package com.wgblackmon.urlshortener.domain.exception;

/**
 * Thrown when an orchestration run is looked up by ID and no matching record exists.
 * Maps to HTTP 404 in the global exception handler.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class OrchestrationRunNotFoundException extends RuntimeException {
    public OrchestrationRunNotFoundException(String runId) {
        super("Orchestration run not found: " + runId);
    }
}

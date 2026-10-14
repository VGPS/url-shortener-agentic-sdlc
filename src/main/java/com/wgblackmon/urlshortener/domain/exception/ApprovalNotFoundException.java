package com.wgblackmon.urlshortener.domain.exception;

/**
 * Thrown when an approval request is looked up by ID and no matching record exists.
 * Maps to HTTP 404 in the global exception handler.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class ApprovalNotFoundException extends RuntimeException {
    public ApprovalNotFoundException(String requestId) {
        super("Approval request not found: " + requestId);
    }
}

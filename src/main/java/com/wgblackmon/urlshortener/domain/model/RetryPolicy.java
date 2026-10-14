package com.wgblackmon.urlshortener.domain.model;

/**
 * Immutable configuration record governing retry behaviour for a single SDLC phase.
 *
 * <h2>Why bounded retries with exponential backoff?</h2>
 * <p>The assignment explicitly requires "bounded retries, fallback, rollback, and
 * safe-stop controls." LLM API calls are prone to transient failures (rate limits,
 * gateway timeouts, malformed responses). Bounded retries ensure the orchestrator
 * eventually escalates to rollback rather than running indefinitely. Exponential
 * backoff prevents thundering-herd re-attempts against an already-overloaded API.
 *
 * <h2>Why is this a separate record rather than fields on PhaseNode?</h2>
 * <p>Different phases have different failure tolerances. REQUIREMENTS failures are
 * cheap to retry (only an LLM prompt); IMPLEMENTATION failures after partial code
 * generation may require rollback sooner. Encapsulating retry configuration in its
 * own record allows per-phase policy without duplicating the retry logic.
 *
 * @param maxAttempts        maximum number of RETRY attempts (0 = no retries, first attempt only)
 * @param initialBackoffMs   wait before the first retry in milliseconds
 * @param backoffMultiplier  multiplier applied to backoff on each subsequent retry
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record RetryPolicy(int maxAttempts, long initialBackoffMs, double backoffMultiplier) {

    public RetryPolicy {
        if (maxAttempts < 0)           throw new IllegalArgumentException("maxAttempts must be ≥ 0");
        if (initialBackoffMs < 0)      throw new IllegalArgumentException("initialBackoffMs must be ≥ 0");
        if (backoffMultiplier < 1.0)   throw new IllegalArgumentException("backoffMultiplier must be ≥ 1.0");
    }

    /** Default policy: 3 retries, 2-second initial backoff, 2× multiplier. */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 2_000, 2.0);
    }

    /** Zero-retry policy — first attempt is the only attempt. Used for phases with manual rollback. */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, 1.0);
    }

    /**
     * Returns the backoff duration in milliseconds for the given retry number (0-based).
     * Retry 0 = initialBackoffMs, retry 1 = initialBackoffMs * multiplier^1, etc.
     */
    public long backoffForAttempt(int retryNumber) {
        double factor = Math.pow(backoffMultiplier, retryNumber);
        return (long) (initialBackoffMs * factor);
    }
}

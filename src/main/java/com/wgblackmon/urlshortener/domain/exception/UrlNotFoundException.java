package com.wgblackmon.urlshortener.domain.exception;

/**
 * Thrown when a short URL code cannot be found or is no longer resolvable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String code) {
        super("Short URL not found or expired: " + code);
    }
}

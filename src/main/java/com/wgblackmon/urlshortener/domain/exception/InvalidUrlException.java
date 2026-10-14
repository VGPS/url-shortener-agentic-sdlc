package com.wgblackmon.urlshortener.domain.exception;

/**
 * Thrown when a submitted URL fails format validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String url) {
        super("Invalid URL — must begin with http:// or https://: " + url);
    }
}

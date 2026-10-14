package com.wgblackmon.urlshortener.domain.exception;

/**
 * Thrown when a requested custom code is already in use.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class CodeAlreadyExistsException extends RuntimeException {
    public CodeAlreadyExistsException(String code) {
        super("Short code already in use: " + code);
    }
}

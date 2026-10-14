package com.wgblackmon.urlshortener.web.dto;

/**
 * Standard error response body returned by {@code GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public record ErrorResponse(String error, String message) {}

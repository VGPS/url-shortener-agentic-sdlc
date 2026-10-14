package com.wgblackmon.urlshortener.domain.port.outbound;

/**
 * Outbound port for generating unique short codes.
 *
 * <p>Implementations must produce collision-resistant alphanumeric strings
 * suitable for use as URL path segments.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface CodeGeneratorPort {
    String generate();
}

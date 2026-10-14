package com.wgblackmon.urlshortener.domain.port.inbound;

import com.wgblackmon.urlshortener.domain.model.ShortUrl;

import java.util.Optional;

/**
 * Inbound port for resolving a short code to its original URL.
 *
 * <p>Returns an empty Optional when the code is unknown, inactive, or expired.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ResolveUrlUseCase {
    Optional<ShortUrl> resolve(String code);
}

package com.wgblackmon.urlshortener.domain.port.inbound;

import com.wgblackmon.urlshortener.domain.model.ShortenRequest;
import com.wgblackmon.urlshortener.domain.model.ShortenResult;

/**
 * Inbound port for shortening a URL.
 *
 * <p>Implementations validate the URL, resolve or generate a short code,
 * persist the mapping, and return a {@link ShortenResult}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ShortenUrlUseCase {
    ShortenResult shorten(ShortenRequest request);
    void deactivate(String code);
}

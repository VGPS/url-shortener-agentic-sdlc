package com.wgblackmon.urlshortener.domain.port.inbound;

import com.wgblackmon.urlshortener.domain.model.ClickEvent;

/**
 * Inbound port for recording a click event on a short URL.
 *
 * <p>Called immediately after a successful redirect. All optional fields
 * ({@code userAgent}, {@code ipAddress}, {@code referrer}) may be null.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface TrackClickUseCase {
    ClickEvent track(String code, String userAgent, String ipAddress, String referrer);
}

package com.wgblackmon.urlshortener.domain.port.inbound;

import com.wgblackmon.urlshortener.domain.model.AnalyticsSummary;

/**
 * Inbound port for retrieving analytics for a short URL.
 *
 * <p>Throws {@link com.wgblackmon.urlshortener.domain.exception.UrlNotFoundException}
 * when the code does not exist.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface GetAnalyticsUseCase {
    AnalyticsSummary getSummary(String code);
}

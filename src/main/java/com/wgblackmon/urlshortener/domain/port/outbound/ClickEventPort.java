package com.wgblackmon.urlshortener.domain.port.outbound;

import com.wgblackmon.urlshortener.domain.model.ClickEvent;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link ClickEvent} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface ClickEventPort {
    ClickEvent record(ClickEvent event);
    List<ClickEvent> findByCode(String code);
    long countByCode(String code);
}

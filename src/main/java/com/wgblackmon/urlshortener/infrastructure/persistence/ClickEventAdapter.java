package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.wgblackmon.urlshortener.domain.model.ClickEvent;
import com.wgblackmon.urlshortener.domain.port.outbound.ClickEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA adapter implementing {@link ClickEventPort}.
 *
 * <p>Maps between the {@link ClickEvent} domain record and
 * {@link ClickEventEntity} for persistence.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Component
public class ClickEventAdapter implements ClickEventPort {

    private final ClickEventRepository repository;

    public ClickEventAdapter(ClickEventRepository repository) {
        log.debug("ClickEventAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public ClickEvent record(ClickEvent event) {
        log.debug("record() | event={}", event);
        ClickEventEntity saved = repository.save(toEntity(event));
        ClickEvent result = toDomain(saved);
        log.debug("record() | return={}", result);
        return result;
    }

    @Override
    public List<ClickEvent> findByCode(String code) {
        log.debug("findByCode() | code={}", code);
        List<ClickEvent> result = repository.findByCode(code).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        log.debug("findByCode() | return size={}", result.size());
        return result;
    }

    @Override
    public long countByCode(String code) {
        log.debug("countByCode() | code={}", code);
        long result = repository.countByCode(code);
        log.debug("countByCode() | return={}", result);
        return result;
    }

    // --- mapping ---

    private ClickEventEntity toEntity(ClickEvent e) {
        return new ClickEventEntity(
                e.eventId(), e.code(), e.clickedAt(),
                e.userAgent(), e.ipAddress(), e.referrer()
        );
    }

    private ClickEvent toDomain(ClickEventEntity e) {
        return new ClickEvent(
                e.getEventId(), e.getCode(), e.getClickedAt(),
                e.getUserAgent(), e.getIpAddress(), e.getReferrer()
        );
    }
}

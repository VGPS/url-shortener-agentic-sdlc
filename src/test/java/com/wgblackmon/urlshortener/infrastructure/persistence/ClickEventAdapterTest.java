package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.wgblackmon.urlshortener.domain.model.ClickEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} slice test for {@link ClickEventAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@DataJpaTest
@Import(ClickEventAdapter.class)
class ClickEventAdapterTest {

    @Autowired
    ClickEventAdapter adapter;

    @Test
    void record_persistsAndReturnsDomainRecord() {
        ClickEvent event = clickEvent("abc123", "https://ref.com");
        ClickEvent saved = adapter.record(event);
        assertThat(saved.code()).isEqualTo("abc123");
        assertThat(saved.referrer()).isEqualTo("https://ref.com");
    }

    @Test
    void findByCode_returnsAllEventsForCode() {
        adapter.record(clickEvent("abc123", null));
        adapter.record(clickEvent("abc123", null));
        adapter.record(clickEvent("other1", null));

        List<ClickEvent> result = adapter.findByCode("abc123");
        assertThat(result).hasSize(2);
    }

    @Test
    void findByCode_unknownCode_returnsEmptyList() {
        assertThat(adapter.findByCode("nope")).isEmpty();
    }

    @Test
    void countByCode_returnsCorrectCount() {
        adapter.record(clickEvent("abc123", null));
        adapter.record(clickEvent("abc123", null));
        assertThat(adapter.countByCode("abc123")).isEqualTo(2);
    }

    @Test
    void countByCode_unknownCode_returnsZero() {
        assertThat(adapter.countByCode("nope")).isZero();
    }

    private ClickEvent clickEvent(String code, String referrer) {
        return new ClickEvent(UUID.randomUUID().toString(), code, Instant.now(),
                "Mozilla/5.0", "127.0.0.1", referrer);
    }
}

package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.wgblackmon.urlshortener.domain.model.ShortUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} slice test for {@link ShortUrlAdapter}.
 *
 * <p>Runs against the H2 in-memory database. Verifies persistence, lookup,
 * and existence checks without loading the full application context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@DataJpaTest
@Import(ShortUrlAdapter.class)
class ShortUrlAdapterTest {

    @Autowired
    ShortUrlAdapter adapter;

    private ShortUrl sample;

    @BeforeEach
    void setUp() {
        sample = new ShortUrl(
                "abc123",
                "https://example.com",
                Instant.now(),
                Instant.now().plusSeconds(86400),
                "testuser",
                true
        );
    }

    @Test
    void save_persistsAndReturnsDomainRecord() {
        ShortUrl saved = adapter.save(sample);
        assertThat(saved.code()).isEqualTo("abc123");
        assertThat(saved.originalUrl()).isEqualTo("https://example.com");
        assertThat(saved.active()).isTrue();
    }

    @Test
    void findByCode_existingCode_returnsShortUrl() {
        adapter.save(sample);
        Optional<ShortUrl> found = adapter.findByCode("abc123");
        assertThat(found).isPresent();
        assertThat(found.get().originalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void findByCode_unknownCode_returnsEmpty() {
        assertThat(adapter.findByCode("nope")).isEmpty();
    }

    @Test
    void findByOriginalUrl_existingUrl_returnsShortUrl() {
        adapter.save(sample);
        Optional<ShortUrl> found = adapter.findByOriginalUrl("https://example.com");
        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("abc123");
    }

    @Test
    void findByOriginalUrl_unknownUrl_returnsEmpty() {
        assertThat(adapter.findByOriginalUrl("https://not-stored.com")).isEmpty();
    }

    @Test
    void existsByCode_existingCode_returnsTrue() {
        adapter.save(sample);
        assertThat(adapter.existsByCode("abc123")).isTrue();
    }

    @Test
    void existsByCode_unknownCode_returnsFalse() {
        assertThat(adapter.existsByCode("nope")).isFalse();
    }

    @Test
    void existsByOriginalUrl_existingUrl_returnsTrue() {
        adapter.save(sample);
        assertThat(adapter.existsByOriginalUrl("https://example.com")).isTrue();
    }
}

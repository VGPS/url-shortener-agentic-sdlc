package com.wgblackmon.urlshortener.domain.service;

import com.wgblackmon.urlshortener.domain.exception.CodeAlreadyExistsException;
import com.wgblackmon.urlshortener.domain.exception.InvalidUrlException;
import com.wgblackmon.urlshortener.domain.exception.UrlNotFoundException;
import com.wgblackmon.urlshortener.domain.model.ClickEvent;
import com.wgblackmon.urlshortener.domain.model.ShortUrl;
import com.wgblackmon.urlshortener.domain.model.ShortenRequest;
import com.wgblackmon.urlshortener.domain.model.ShortenResult;
import com.wgblackmon.urlshortener.domain.port.outbound.ClickEventPort;
import com.wgblackmon.urlshortener.domain.port.outbound.CodeGeneratorPort;
import com.wgblackmon.urlshortener.domain.port.outbound.ShortUrlPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UrlShorteningService}.
 *
 * <p>All outbound ports are mocked — no Spring context or database required.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@ExtendWith(MockitoExtension.class)
class UrlShorteningServiceTest {

    @Mock ShortUrlPort shortUrlPort;
    @Mock ClickEventPort clickEventPort;
    @Mock CodeGeneratorPort codeGeneratorPort;

    private UrlShorteningService service;

    @BeforeEach
    void setUp() {
        service = new UrlShorteningService(shortUrlPort, clickEventPort, codeGeneratorPort,
                "http://localhost:8080");
    }

    @Test
    void shorten_validUrl_returnsShortenResult() {
        when(shortUrlPort.findByOriginalUrl(anyString())).thenReturn(Optional.empty());
        when(codeGeneratorPort.generate()).thenReturn("abc123");
        when(shortUrlPort.existsByCode("abc123")).thenReturn(false);
        when(shortUrlPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShortenResult result = service.shorten(new ShortenRequest("https://example.com", null, null, null));

        assertThat(result.shortCode()).isEqualTo("abc123");
        assertThat(result.fullShortLink()).isEqualTo("http://localhost:8080/abc123");
        assertThat(result.alreadyExisted()).isFalse();
    }

    @Test
    void shorten_invalidUrl_throwsInvalidUrlException() {
        assertThatThrownBy(() -> service.shorten(new ShortenRequest("ftp://bad.com", null, null, null)))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void shorten_customCode_usesProvidedCode() {
        when(shortUrlPort.findByOriginalUrl(anyString())).thenReturn(Optional.empty());
        when(shortUrlPort.existsByCode("mycode")).thenReturn(false);
        when(shortUrlPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShortenResult result = service.shorten(new ShortenRequest("https://example.com", "mycode", null, null));

        assertThat(result.shortCode()).isEqualTo("mycode");
    }

    @Test
    void shorten_customCodeAlreadyExists_throwsCodeAlreadyExistsException() {
        when(shortUrlPort.findByOriginalUrl(anyString())).thenReturn(Optional.empty());
        when(shortUrlPort.existsByCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten(new ShortenRequest("https://example.com", "taken", null, null)))
                .isInstanceOf(CodeAlreadyExistsException.class);
    }

    @Test
    void shorten_urlAlreadyExists_returnsExistingWithFlag() {
        ShortUrl existing = new ShortUrl("exist1", "https://example.com",
                Instant.now(), Instant.now().plusSeconds(86400), null, true);
        when(shortUrlPort.findByOriginalUrl("https://example.com")).thenReturn(Optional.of(existing));

        ShortenResult result = service.shorten(new ShortenRequest("https://example.com", null, null, null));

        assertThat(result.alreadyExisted()).isTrue();
        assertThat(result.shortCode()).isEqualTo("exist1");
    }

    @Test
    void shorten_codeCollisionRetries_succeedsOnSecondAttempt() {
        when(shortUrlPort.findByOriginalUrl(anyString())).thenReturn(Optional.empty());
        when(codeGeneratorPort.generate()).thenReturn("taken1", "free01");
        when(shortUrlPort.existsByCode("taken1")).thenReturn(true);
        when(shortUrlPort.existsByCode("free01")).thenReturn(false);
        when(shortUrlPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShortenResult result = service.shorten(new ShortenRequest("https://example.com", null, null, null));

        assertThat(result.shortCode()).isEqualTo("free01");
    }

    @Test
    void resolve_activeNonExpiredCode_returnsShortUrl() {
        ShortUrl shortUrl = new ShortUrl("abc123", "https://example.com",
                Instant.now(), Instant.now().plusSeconds(86400), null, true);
        when(shortUrlPort.findByCode("abc123")).thenReturn(Optional.of(shortUrl));

        Optional<ShortUrl> result = service.resolve("abc123");

        assertThat(result).isPresent();
        assertThat(result.get().originalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void resolve_unknownCode_returnsEmpty() {
        when(shortUrlPort.findByCode("nope")).thenReturn(Optional.empty());

        assertThat(service.resolve("nope")).isEmpty();
    }

    @Test
    void resolve_expiredCode_returnsEmpty() {
        ShortUrl expired = new ShortUrl("old123", "https://example.com",
                Instant.now().minusSeconds(1000), Instant.now().minusSeconds(1), null, true);
        when(shortUrlPort.findByCode("old123")).thenReturn(Optional.of(expired));

        assertThat(service.resolve("old123")).isEmpty();
    }

    @Test
    void track_validCode_returnsClickEvent() {
        when(clickEventPort.record(any())).thenAnswer(inv -> inv.getArgument(0));

        ClickEvent event = service.track("abc123", "Mozilla/5.0", "127.0.0.1", "https://referrer.com");

        assertThat(event.code()).isEqualTo("abc123");
        assertThat(event.ipAddress()).isEqualTo("127.0.0.1");
        assertThat(event.eventId()).isNotBlank();
    }

    @Test
    void getSummary_existingCode_returnsAggregatedSummary() {
        ShortUrl shortUrl = new ShortUrl("abc123", "https://example.com",
                Instant.now(), Instant.now().plusSeconds(86400), null, true);
        ClickEvent e1 = new ClickEvent("e1", "abc123", Instant.now(), null, null, "https://ref.com");
        ClickEvent e2 = new ClickEvent("e2", "abc123", Instant.now(), null, null, "https://ref.com");

        when(shortUrlPort.findByCode("abc123")).thenReturn(Optional.of(shortUrl));
        when(clickEventPort.findByCode("abc123")).thenReturn(List.of(e1, e2));

        var summary = service.getSummary("abc123");

        assertThat(summary.totalClicks()).isEqualTo(2);
        assertThat(summary.topReferrers()).containsExactly("https://ref.com");
    }

    @Test
    void getSummary_unknownCode_throwsUrlNotFoundException() {
        when(shortUrlPort.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary("nope"))
                .isInstanceOf(UrlNotFoundException.class);
    }
}

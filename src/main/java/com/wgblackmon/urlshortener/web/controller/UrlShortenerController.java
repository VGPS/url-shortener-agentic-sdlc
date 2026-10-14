package com.wgblackmon.urlshortener.web.controller;

import com.wgblackmon.urlshortener.domain.exception.UrlNotFoundException;
import com.wgblackmon.urlshortener.domain.model.AnalyticsSummary;
import com.wgblackmon.urlshortener.domain.model.ShortenRequest;
import com.wgblackmon.urlshortener.domain.model.ShortenResult;
import com.wgblackmon.urlshortener.domain.model.ShortUrl;
import com.wgblackmon.urlshortener.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.ShortenUrlUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.TrackClickUseCase;
import com.wgblackmon.urlshortener.web.dto.AnalyticsSummaryDto;
import com.wgblackmon.urlshortener.web.dto.ShortenRequestDto;
import com.wgblackmon.urlshortener.web.dto.ShortenResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for URL shortener operations.
 *
 * <p>Handles shortening, detail lookup, deactivation, and analytics.
 * The redirect endpoint (GET /{code}) lives in {@link RedirectController}
 * to separate the public-facing redirect from the API surface.
 *
 * <p>All methods delegate to inbound use-case interfaces — no direct
 * dependency on the domain service or persistence layer.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/urls")
public class UrlShortenerController {

    private final ShortenUrlUseCase  shortenUrlUseCase;
    private final ResolveUrlUseCase  resolveUrlUseCase;
    private final GetAnalyticsUseCase getAnalyticsUseCase;

    public UrlShortenerController(ShortenUrlUseCase shortenUrlUseCase,
                                  ResolveUrlUseCase resolveUrlUseCase,
                                  GetAnalyticsUseCase getAnalyticsUseCase) {
        log.debug("UrlShortenerController() | wired");
        this.shortenUrlUseCase  = shortenUrlUseCase;
        this.resolveUrlUseCase  = resolveUrlUseCase;
        this.getAnalyticsUseCase = getAnalyticsUseCase;
    }

    /** POST /api/v1/urls — shorten a URL, returns 201 Created. */
    @PostMapping
    public ResponseEntity<ShortenResponseDto> shorten(@Valid @RequestBody ShortenRequestDto dto) {
        log.debug("shorten() | dto={}", dto);
        ShortenResult result = shortenUrlUseCase.shorten(
                new ShortenRequest(dto.originalUrl(), dto.customCode(), dto.ttlDays(), null)
        );
        ShortenResponseDto response = toResponse(result);
        log.debug("shorten() | return={}", response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/urls/" + result.shortCode()))
                .body(response);
    }

    /** GET /api/v1/urls/{code} — get short URL details, returns 200 or 404. */
    @GetMapping("/{code}")
    public ResponseEntity<ShortenResponseDto> getDetails(@PathVariable String code) {
        log.debug("getDetails() | code={}", code);
        ShortUrl shortUrl = resolveUrlUseCase.resolve(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        ShortenResponseDto response = toDetailResponse(shortUrl);
        log.debug("getDetails() | return={}", response);
        return ResponseEntity.ok(response);
    }

    /** DELETE /api/v1/urls/{code} — deactivate a short URL, returns 204. */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deactivate(@PathVariable String code) {
        log.debug("deactivate() | code={}", code);
        shortenUrlUseCase.deactivate(code);
        log.debug("deactivate() | return=void");
        return ResponseEntity.noContent().build();
    }

    /** GET /api/v1/urls/{code}/analytics — click analytics, returns 200 or 404. */
    @GetMapping("/{code}/analytics")
    public ResponseEntity<AnalyticsSummaryDto> getAnalytics(@PathVariable String code) {
        log.debug("getAnalytics() | code={}", code);
        AnalyticsSummary summary = getAnalyticsUseCase.getSummary(code);
        AnalyticsSummaryDto response = toAnalyticsDto(summary);
        log.debug("getAnalytics() | return={}", response);
        return ResponseEntity.ok(response);
    }

    // --- mapping ---

    private ShortenResponseDto toResponse(ShortenResult result) {
        ShortUrl s = result.shortUrl();
        return new ShortenResponseDto(
                result.shortCode(), result.fullShortLink(),
                s.originalUrl(), s.expiresAt(), s.active(), result.alreadyExisted()
        );
    }

    private ShortenResponseDto toDetailResponse(ShortUrl s) {
        return new ShortenResponseDto(
                s.code(), null, s.originalUrl(), s.expiresAt(), s.active(), false
        );
    }

    private AnalyticsSummaryDto toAnalyticsDto(AnalyticsSummary s) {
        Map<String, Long> clicksByDay = s.clicksByDay().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
        return new AnalyticsSummaryDto(
                s.code(), s.totalClicks(), clicksByDay,
                s.topReferrers(), s.lastClickedAt()
        );
    }
}

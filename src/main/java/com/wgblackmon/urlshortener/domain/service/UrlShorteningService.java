package com.wgblackmon.urlshortener.domain.service;

import com.wgblackmon.urlshortener.domain.exception.CodeAlreadyExistsException;
import com.wgblackmon.urlshortener.domain.exception.InvalidUrlException;
import com.wgblackmon.urlshortener.domain.exception.UrlNotFoundException;
import com.wgblackmon.urlshortener.domain.model.AnalyticsSummary;
import com.wgblackmon.urlshortener.domain.model.ClickEvent;
import com.wgblackmon.urlshortener.domain.model.ShortUrl;
import com.wgblackmon.urlshortener.domain.model.ShortenRequest;
import com.wgblackmon.urlshortener.domain.model.ShortenResult;
import com.wgblackmon.urlshortener.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.ShortenUrlUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.TrackClickUseCase;
import com.wgblackmon.urlshortener.domain.port.outbound.ClickEventPort;
import com.wgblackmon.urlshortener.domain.port.outbound.CodeGeneratorPort;
import com.wgblackmon.urlshortener.domain.port.outbound.ShortUrlPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core domain service orchestrating URL shortening, resolution, click tracking,
 * and analytics retrieval.
 *
 * <p>Implements all four URL shortener inbound use cases. Contains no Spring or
 * Lombok dependencies — pure JDK only, following domain purity rules. Uses
 * {@link System.Logger} for tracing since {@code @Slf4j} is unavailable in the
 * domain layer.
 *
 * <p>Code generation retries up to {@code MAX_CODE_RETRIES} times on collision
 * before propagating the failure. Custom codes are validated for uniqueness before
 * use. If a URL has already been shortened, the existing code is returned with
 * {@code alreadyExisted=true}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class UrlShorteningService
        implements ShortenUrlUseCase, ResolveUrlUseCase, TrackClickUseCase, GetAnalyticsUseCase {

    private static final System.Logger log = System.getLogger(UrlShorteningService.class.getName());
    private static final int MAX_CODE_RETRIES = 5;
    private static final int DEFAULT_TTL_DAYS = 365;

    private final ShortUrlPort shortUrlPort;
    private final ClickEventPort clickEventPort;
    private final CodeGeneratorPort codeGeneratorPort;
    private final String baseUrl;

    public UrlShorteningService(ShortUrlPort shortUrlPort,
                                ClickEventPort clickEventPort,
                                CodeGeneratorPort codeGeneratorPort,
                                String baseUrl) {
        log.log(System.Logger.Level.DEBUG,
                "UrlShorteningService() | shortUrlPort={0}, clickEventPort={1}, codeGeneratorPort={2}, baseUrl={3}",
                shortUrlPort, clickEventPort, codeGeneratorPort, baseUrl);
        this.shortUrlPort      = shortUrlPort;
        this.clickEventPort    = clickEventPort;
        this.codeGeneratorPort = codeGeneratorPort;
        this.baseUrl           = baseUrl;
    }

    @Override
    public ShortenResult shorten(ShortenRequest request) {
        log.log(System.Logger.Level.DEBUG, "shorten() | request={0}", request);

        validateUrl(request.originalUrl());

        // Return existing mapping if URL already shortened
        Optional<ShortUrl> existing = shortUrlPort.findByOriginalUrl(request.originalUrl());
        if (existing.isPresent()) {
            ShortenResult result = buildResult(existing.get(), true);
            log.log(System.Logger.Level.DEBUG, "shorten() | return={0}", result);
            return result;
        }

        String code = resolveCode(request);
        int ttlDays = request.ttlDays() != null ? request.ttlDays() : DEFAULT_TTL_DAYS;
        Instant now = Instant.now();

        ShortUrl shortUrl = new ShortUrl(
                code,
                request.originalUrl(),
                now,
                now.plusSeconds((long) ttlDays * 86_400),
                request.createdBy(),
                true
        );

        ShortUrl saved = shortUrlPort.save(shortUrl);
        ShortenResult result = buildResult(saved, false);
        log.log(System.Logger.Level.DEBUG, "shorten() | return={0}", result);
        return result;
    }

    @Override
    public Optional<ShortUrl> resolve(String code) {
        log.log(System.Logger.Level.DEBUG, "resolve() | code={0}", code);
        Optional<ShortUrl> result = shortUrlPort.findByCode(code)
                .filter(ShortUrl::isResolvable);
        log.log(System.Logger.Level.DEBUG, "resolve() | return={0}", result);
        return result;
    }

    @Override
    public ClickEvent track(String code, String userAgent, String ipAddress, String referrer) {
        log.log(System.Logger.Level.DEBUG,
                "track() | code={0}, userAgent={1}, ipAddress={2}, referrer={3}",
                code, userAgent, ipAddress, referrer);
        ClickEvent event = new ClickEvent(
                UUID.randomUUID().toString(),
                code,
                Instant.now(),
                userAgent,
                ipAddress,
                referrer
        );
        ClickEvent saved = clickEventPort.record(event);
        log.log(System.Logger.Level.DEBUG, "track() | return={0}", saved);
        return saved;
    }

    @Override
    public AnalyticsSummary getSummary(String code) {
        log.log(System.Logger.Level.DEBUG, "getSummary() | code={0}", code);

        shortUrlPort.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));

        List<ClickEvent> events = clickEventPort.findByCode(code);

        Map<LocalDate, Long> clicksByDay = events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.clickedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()
                ));

        List<String> topReferrers = events.stream()
                .filter(e -> e.referrer() != null && !e.referrer().isBlank())
                .collect(Collectors.groupingBy(ClickEvent::referrer, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Instant lastClickedAt = events.stream()
                .map(ClickEvent::clickedAt)
                .max(Instant::compareTo)
                .orElse(null);

        AnalyticsSummary result = new AnalyticsSummary(
                code, events.size(), clicksByDay, topReferrers, lastClickedAt
        );
        log.log(System.Logger.Level.DEBUG, "getSummary() | return={0}", result);
        return result;
    }

    @Override
    public void deactivate(String code) {
        log.log(System.Logger.Level.DEBUG, "deactivate() | code={0}", code);
        ShortUrl existing = shortUrlPort.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        ShortUrl deactivated = new ShortUrl(
                existing.code(), existing.originalUrl(), existing.createdAt(),
                existing.expiresAt(), existing.createdBy(), false
        );
        shortUrlPort.save(deactivated);
        log.log(System.Logger.Level.DEBUG, "deactivate() | return=void");
    }

    // --- private helpers ---

    private void validateUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new InvalidUrlException(url);
        }
    }

    private String resolveCode(ShortenRequest request) {
        if (request.customCode() != null && !request.customCode().isBlank()) {
            if (shortUrlPort.existsByCode(request.customCode())) {
                throw new CodeAlreadyExistsException(request.customCode());
            }
            return request.customCode();
        }
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            String candidate = codeGeneratorPort.generate();
            if (!shortUrlPort.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Code generation failed after " + MAX_CODE_RETRIES + " attempts");
    }

    private ShortenResult buildResult(ShortUrl shortUrl, boolean alreadyExisted) {
        return new ShortenResult(
                shortUrl,
                shortUrl.code(),
                baseUrl + "/" + shortUrl.code(),
                alreadyExisted
        );
    }
}

package com.wgblackmon.urlshortener.web.controller;

import com.wgblackmon.urlshortener.domain.exception.UrlNotFoundException;
import com.wgblackmon.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.TrackClickUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public-facing redirect controller.
 *
 * <p>Handles {@code GET /{code}} — resolves the short code to its original
 * URL, records the click event, and returns a 302 redirect. Returns 404
 * when the code is unknown, inactive, or expired.
 *
 * <p>Kept separate from {@link UrlShortenerController} so the redirect
 * path ({@code /{code}}) does not interfere with the {@code /api/v1/} surface.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@RestController
public class RedirectController {

    private final ResolveUrlUseCase resolveUrlUseCase;
    private final TrackClickUseCase trackClickUseCase;

    public RedirectController(ResolveUrlUseCase resolveUrlUseCase,
                              TrackClickUseCase trackClickUseCase) {
        log.debug("RedirectController() | wired");
        this.resolveUrlUseCase = resolveUrlUseCase;
        this.trackClickUseCase = trackClickUseCase;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code,
                                         HttpServletRequest request) {
        log.debug("redirect() | code={}", code);
        return resolveUrlUseCase.resolve(code)
                .map(shortUrl -> {
                    trackClickUseCase.track(
                            code,
                            request.getHeader("User-Agent"),
                            request.getRemoteAddr(),
                            request.getHeader("Referer")
                    );
                    ResponseEntity<Void> result = ResponseEntity
                            .status(HttpStatus.FOUND)
                            .location(URI.create(shortUrl.originalUrl()))
                            .build();
                    log.debug("redirect() | return=302 to {}", shortUrl.originalUrl());
                    return result;
                })
                .orElseThrow(() -> new UrlNotFoundException(code));
    }
}

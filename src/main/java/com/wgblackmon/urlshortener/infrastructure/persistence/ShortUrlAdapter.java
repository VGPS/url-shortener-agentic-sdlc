package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.wgblackmon.urlshortener.domain.model.ShortUrl;
import com.wgblackmon.urlshortener.domain.port.outbound.ShortUrlPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA adapter implementing {@link ShortUrlPort}.
 *
 * <p>Maps between the {@link ShortUrl} domain record and {@link ShortUrlEntity}
 * for persistence. All mapping logic is contained here — the domain layer
 * has no knowledge of JPA.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Component
public class ShortUrlAdapter implements ShortUrlPort {

    private final ShortUrlRepository repository;

    public ShortUrlAdapter(ShortUrlRepository repository) {
        log.debug("ShortUrlAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public ShortUrl save(ShortUrl shortUrl) {
        log.debug("save() | shortUrl={}", shortUrl);
        ShortUrlEntity entity = toEntity(shortUrl);
        ShortUrlEntity saved  = repository.save(entity);
        ShortUrl result = toDomain(saved);
        log.debug("save() | return={}", result);
        return result;
    }

    @Override
    public Optional<ShortUrl> findByCode(String code) {
        log.debug("findByCode() | code={}", code);
        Optional<ShortUrl> result = repository.findById(code).map(this::toDomain);
        log.debug("findByCode() | return={}", result);
        return result;
    }

    @Override
    public Optional<ShortUrl> findByOriginalUrl(String originalUrl) {
        log.debug("findByOriginalUrl() | originalUrl={}", originalUrl);
        Optional<ShortUrl> result = repository.findByOriginalUrl(originalUrl).map(this::toDomain);
        log.debug("findByOriginalUrl() | return={}", result);
        return result;
    }

    @Override
    public boolean existsByCode(String code) {
        log.debug("existsByCode() | code={}", code);
        boolean result = repository.existsById(code);
        log.debug("existsByCode() | return={}", result);
        return result;
    }

    @Override
    public boolean existsByOriginalUrl(String originalUrl) {
        log.debug("existsByOriginalUrl() | originalUrl={}", originalUrl);
        boolean result = repository.existsByOriginalUrl(originalUrl);
        log.debug("existsByOriginalUrl() | return={}", result);
        return result;
    }

    // --- mapping ---

    private ShortUrlEntity toEntity(ShortUrl s) {
        return new ShortUrlEntity(
                s.code(), s.originalUrl(), s.createdAt(),
                s.expiresAt(), s.createdBy(), s.active()
        );
    }

    private ShortUrl toDomain(ShortUrlEntity e) {
        return new ShortUrl(
                e.getCode(), e.getOriginalUrl(), e.getCreatedAt(),
                e.getExpiresAt(), e.getCreatedBy(), e.isActive()
        );
    }
}

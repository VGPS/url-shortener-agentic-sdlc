package com.wgblackmon.urlshortener.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for the {@code short_urls} table.
 *
 * <p>The natural key is {@code code} — the 6-character alphanumeric short code.
 * {@code originalUrl} is stored as VARCHAR(2048) to accommodate long URLs.
 * {@code expiresAt} and {@code createdBy} are nullable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Entity
@Table(name = "short_urls")
public class ShortUrlEntity {

    @Id
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "original_url", length = 2048, nullable = false)
    private String originalUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ShortUrlEntity() {}

    public ShortUrlEntity(String code, String originalUrl, Instant createdAt,
                          Instant expiresAt, String createdBy, boolean active) {
        this.code        = code;
        this.originalUrl = originalUrl;
        this.createdAt   = createdAt;
        this.expiresAt   = expiresAt;
        this.createdBy   = createdBy;
        this.active      = active;
    }

    public String getCode()        { return code; }
    public String getOriginalUrl() { return originalUrl; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getExpiresAt()  { return expiresAt; }
    public String getCreatedBy()   { return createdBy; }
    public boolean isActive()      { return active; }
    public void setActive(boolean active) { this.active = active; }
}

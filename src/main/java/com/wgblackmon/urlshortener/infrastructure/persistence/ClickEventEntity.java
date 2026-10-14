package com.wgblackmon.urlshortener.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for the {@code click_events} table.
 *
 * <p>{@code eventId} is a UUID string PK assigned at creation time.
 * {@code userAgent}, {@code ipAddress}, and {@code referrer} are all
 * nullable — callers may omit them for performance or privacy reasons.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Entity
@Table(name = "click_events")
public class ClickEventEntity {

    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "referrer", length = 2048)
    private String referrer;

    protected ClickEventEntity() {}

    public ClickEventEntity(String eventId, String code, Instant clickedAt,
                            String userAgent, String ipAddress, String referrer) {
        this.eventId   = eventId;
        this.code      = code;
        this.clickedAt = clickedAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.referrer  = referrer;
    }

    public String getEventId()   { return eventId; }
    public String getCode()      { return code; }
    public Instant getClickedAt(){ return clickedAt; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public String getReferrer()  { return referrer; }
}

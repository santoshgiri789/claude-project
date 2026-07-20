// Service layer — response DTO
// Implements: REQ-SHORT-005, REQ-SHORT-006 (click count, last accessed, referrer history).
package com.business.urlshortener.dto;

import com.business.urlshortener.entity.Url;
import java.time.Instant;
import java.util.List;

public class AnalyticsResponse {

    private final String shortCode;
    private final String originalUrl;
    private final long clickCount;
    private final Instant lastAccessedAt;
    private final List<String> referrers;
    private final Instant createdAt;
    private final Instant expiresAt;

    public AnalyticsResponse(Url url, List<String> referrers) {
        this.shortCode = url.getShortCode();
        this.originalUrl = url.getOriginalUrl();
        this.clickCount = url.getClickCount();
        this.lastAccessedAt = url.getLastAccessedAt();
        this.referrers = referrers;
        this.createdAt = url.getCreatedAt();
        this.expiresAt = url.getExpiresAt();
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public List<String> getReferrers() {
        return referrers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
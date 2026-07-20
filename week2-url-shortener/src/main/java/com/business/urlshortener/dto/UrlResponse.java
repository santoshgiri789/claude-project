// Service layer — response DTO
// Implements: REQ-SHORT-001 (short_code + short_url), REQ-SHORT-009 (existing vs new).
package com.business.urlshortener.dto;

import com.business.urlshortener.entity.Url;
import java.time.Instant;

public class UrlResponse {

    private final String shortCode;
    private final String shortUrl;
    private final String originalUrl;
    private final Instant expiresAt;
    private final Instant createdAt;

    // True when this call created a new mapping (HTTP 201); false when an
    // existing duplicate was returned (HTTP 200) — REQ-SHORT-009.
    private final boolean created;

    public UrlResponse(Url url, String shortUrl, boolean created) {
        this.shortCode = url.getShortCode();
        this.shortUrl = shortUrl;
        this.originalUrl = url.getOriginalUrl();
        this.expiresAt = url.getExpiresAt();
        this.createdAt = url.getCreatedAt();
        this.created = created;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isCreated() {
        return created;
    }
}
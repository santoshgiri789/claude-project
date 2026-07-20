// Service layer — request DTO
// Implements: REQ-SHORT-001 (shorten request payload),
//             REQ-SHORT-007 (optional expiry).
package com.business.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class CreateUrlRequest {

    @NotBlank(message = "url must not be blank")
    private String url;

    // Optional ISO-8601 expiry; null means the short URL never expires.
    private Instant expiresAt;

    public CreateUrlRequest() {
    }

    public CreateUrlRequest(String url, Instant expiresAt) {
        this.url = url;
        this.expiresAt = expiresAt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
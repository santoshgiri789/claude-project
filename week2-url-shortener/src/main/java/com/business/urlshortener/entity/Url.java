// TASK-02 — Domain entities & repositories
// Implements: REQ-SHORT-002 (unique, indexed URL-safe short code),
//             REQ-SHORT-005 (denormalized click_count / last_accessed_at analytics),
//             REQ-SHORT-006 (analytics source via click events),
//             REQ-SHORT-007 (optional expiry via nullable expires_at + status).
package com.business.urlshortener.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A shortened URL and its denormalized analytics aggregates.
 * The {@code short_code} column is unique and indexed for O(1) lookups.
 */
@Entity
@Table(
        name = "url",
        uniqueConstraints = @UniqueConstraint(name = "uk_url_short_code", columnNames = "short_code")
)
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 16)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0L;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Nullable: a null value means the short URL never expires (REQ-SHORT-007).
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UrlStatus status = UrlStatus.ACTIVE;

    @OneToMany(mappedBy = "url", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ClickEvent> clickEvents = new ArrayList<>();

    protected Url() {
        // Required by JPA.
    }

    public Url(String shortCode, String originalUrl, Instant expiresAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = UrlStatus.ACTIVE;
        }
    }

    /** True when an expiry is set and has already passed. */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /** Adds a click event and keeps both sides of the relationship in sync. */
    public void addClickEvent(ClickEvent event) {
        clickEvents.add(event);
        event.setUrl(this);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UrlStatus getStatus() {
        return status;
    }

    public void setStatus(UrlStatus status) {
        this.status = status;
    }

    public List<ClickEvent> getClickEvents() {
        return clickEvents;
    }
}
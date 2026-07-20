// TASK-02 — Domain entities & repositories
// Implements: REQ-SHORT-005 (records referrer + timestamp per redirect),
//             REQ-SHORT-006 (per-hit referrer history for the analytics endpoint).
package com.business.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single redirect hit against a {@link Url}, preserving referrer history.
 */
@Entity
@Table(
        name = "click_event",
        indexes = @Index(name = "idx_click_event_url_id", columnList = "url_id")
)
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "url_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_click_event_url"))
    private Url url;

    // Nullable HTTP Referer header.
    @Column(name = "referrer", length = 2048)
    private String referrer;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt;

    protected ClickEvent() {
        // Required by JPA.
    }

    public ClickEvent(String referrer, String clientIp) {
        this.referrer = referrer;
        this.clientIp = clientIp;
    }

    @PrePersist
    void onCreate() {
        if (accessedAt == null) {
            accessedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }
}
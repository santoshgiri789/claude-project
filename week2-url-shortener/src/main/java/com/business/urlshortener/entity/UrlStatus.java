// TASK-02 — Domain entities & repositories
// Implements: REQ-SHORT-007 (lifecycle status used for expiry handling).
package com.business.urlshortener.entity;

/**
 * Lifecycle state of a shortened URL.
 * See specs/diagrams/state.md (ACTIVE -> EXPIRED -> DELETED).
 */
public enum UrlStatus {
    ACTIVE,
    EXPIRED,
    DELETED
}
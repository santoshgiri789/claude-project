// TASK-02 — Domain entities & repositories
// Implements: REQ-SHORT-002 (unique short-code lookup / uniqueness check),
//             REQ-SHORT-003 & REQ-SHORT-004 (resolve short code for redirect / not-found),
//             REQ-SHORT-009 (duplicate detection via original-URL lookup).
package com.business.urlshortener.repository;

import com.business.urlshortener.entity.Url;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    /** Resolve a short code (indexed unique column) — used by redirect and analytics. */
    Optional<Url> findByShortCode(String shortCode);

    /** Duplicate detection: find an existing mapping for the same original URL. */
    Optional<Url> findByOriginalUrl(String originalUrl);

    /** Fast uniqueness check for short-code generation. */
    boolean existsByShortCode(String shortCode);
}
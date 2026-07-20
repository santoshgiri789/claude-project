// TASK-02 — Domain entities & repositories
// Implements: REQ-SHORT-005 (persist per-redirect click events),
//             REQ-SHORT-006 (retrieve referrer history for analytics).
package com.business.urlshortener.repository;

import com.business.urlshortener.entity.ClickEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /** Referrer/click history for a short code, most recent first. */
    List<ClickEvent> findByUrlShortCodeOrderByAccessedAtDesc(String shortCode);
}
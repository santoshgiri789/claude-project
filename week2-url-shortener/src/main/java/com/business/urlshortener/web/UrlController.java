// TASK-05 & TASK-07 — Shorten and analytics endpoints
// Implements: REQ-SHORT-001, 002, 008, 009, 010 (POST /api/v1/urls),
//             REQ-SHORT-005, 006 (GET /api/v1/urls/{shortCode}/analytics).
package com.business.urlshortener.web;

import com.business.urlshortener.dto.AnalyticsResponse;
import com.business.urlshortener.dto.CreateUrlRequest;
import com.business.urlshortener.dto.UrlResponse;
import com.business.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Shorten a URL. Returns 201 Created for a new mapping, or 200 OK with the existing
     * short code when the URL was already shortened (REQ-SHORT-009).
     * REQ-SHORT-001, 002, 008, 010.
     */
    @PostMapping
    public ResponseEntity<UrlResponse> shorten(@Valid @RequestBody CreateUrlRequest request) {
        UrlResponse response = urlService.shorten(request);
        HttpStatus status = response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Return analytics (click count, last accessed, referrers) for a short code.
     * Unknown code -> 404 via the global handler. REQ-SHORT-005, 006.
     */
    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<AnalyticsResponse> analytics(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.getStats(shortCode));
    }
}
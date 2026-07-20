// TASK-06 — Redirect endpoint
// Implements: REQ-SHORT-003 (302 redirect to original URL),
//             REQ-SHORT-004 (unknown code -> 404), REQ-SHORT-005 (record click + referrer),
//             REQ-SHORT-007 (expired -> 410), REQ-SHORT-012 (only stored URLs are targets).
package com.business.urlshortener.web;

import com.business.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Resolve a short code and issue a 302 redirect to the original URL, recording the
     * click and referrer. Not-found -> 404, expired -> 410 (via the global handler).
     * REQ-SHORT-003, 004, 005, 007, 012.
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = "Referer", required = false) String referer,
            HttpServletRequest request) {

        String target = urlService.resolveAndRecordClick(shortCode, referer, clientIp(request));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }

    /** Prefer the left-most X-Forwarded-For entry, falling back to the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
// Service layer implementation (TASK-05, TASK-06, TASK-07)
// Implements: REQ-SHORT-001, 002, 003, 004, 005, 006, 007, 008, 009, 010, 012.
package com.business.urlshortener.service;

import com.business.urlshortener.dto.AnalyticsResponse;
import com.business.urlshortener.dto.CreateUrlRequest;
import com.business.urlshortener.dto.UrlResponse;
import com.business.urlshortener.entity.ClickEvent;
import com.business.urlshortener.entity.Url;
import com.business.urlshortener.entity.UrlStatus;
import com.business.urlshortener.exception.GoneException;
import com.business.urlshortener.exception.NotFoundException;
import com.business.urlshortener.repository.ClickEventRepository;
import com.business.urlshortener.repository.UrlRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final UrlValidator urlValidator;
    private final ShortCodeGenerator shortCodeGenerator;
    private final String baseUrl;

    public UrlServiceImpl(UrlRepository urlRepository,
                          ClickEventRepository clickEventRepository,
                          UrlValidator urlValidator,
                          ShortCodeGenerator shortCodeGenerator,
                          @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
        this.urlValidator = urlValidator;
        this.shortCodeGenerator = shortCodeGenerator;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    /**
     * Shortens a URL. Rejects invalid / malicious URLs (REQ-SHORT-008, REQ-SHORT-010),
     * returns the existing code for duplicates (REQ-SHORT-009), otherwise generates a new
     * unique code (REQ-SHORT-001, REQ-SHORT-002).
     */
    @Override
    @Transactional
    public UrlResponse shorten(CreateUrlRequest request) {
        String rawUrl = request.getUrl() == null ? null : request.getUrl().trim();

        // REQ-SHORT-008 / REQ-SHORT-010 — reject invalid or malicious URLs before storing.
        urlValidator.validate(rawUrl);

        // REQ-SHORT-009 — duplicate detection: return the existing mapping unchanged.
        Optional<Url> existing = urlRepository.findByOriginalUrl(rawUrl);
        if (existing.isPresent()) {
            return toResponse(existing.get(), false);
        }

        // REQ-SHORT-001 / REQ-SHORT-002 — create a new mapping with a unique short code.
        String shortCode = shortCodeGenerator.generate();
        Url url = new Url(shortCode, rawUrl, request.getExpiresAt());
        Url saved = urlRepository.save(url);
        return toResponse(saved, true);
    }

    /**
     * Resolves a short code to its original URL and records the click.
     * Unknown code -> 404 (REQ-SHORT-004); expired/deleted -> 410 (REQ-SHORT-007);
     * otherwise updates analytics (REQ-SHORT-005) and returns the target (REQ-SHORT-003,
     * REQ-SHORT-012 — only a previously validated, stored URL is returned).
     */
    @Override
    @Transactional
    public String resolveAndRecordClick(String shortCode, String referrer, String clientIp) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Short code not found: " + shortCode));

        if (url.getStatus() == UrlStatus.DELETED) {
            throw new NotFoundException("Short code not found: " + shortCode);
        }

        // REQ-SHORT-007 — honor expiry: mark expired and refuse the redirect.
        if (url.getStatus() == UrlStatus.EXPIRED || url.isExpired()) {
            if (url.getStatus() != UrlStatus.EXPIRED) {
                url.setStatus(UrlStatus.EXPIRED);
                urlRepository.save(url);
            }
            throw new GoneException("Short URL has expired: " + shortCode);
        }

        // REQ-SHORT-012 — defense in depth against open redirect: never emit a redirect
        // to a target that would not pass validation (e.g. a tampered/legacy row).
        String target = url.getOriginalUrl();
        if (target == null || !(target.startsWith("http://") || target.startsWith("https://"))) {
            throw new NotFoundException("Short code not found: " + shortCode);
        }

        // REQ-SHORT-005 — record analytics for this hit.
        url.incrementClickCount();
        url.setLastAccessedAt(Instant.now());
        url.addClickEvent(new ClickEvent(referrer, clientIp));
        urlRepository.save(url);

        return target;
    }

    /**
     * Returns analytics for a short code: click count, last accessed timestamp, and
     * referrer history. Unknown code -> 404. REQ-SHORT-005, REQ-SHORT-006.
     */
    @Override
    @Transactional(readOnly = true)
    public AnalyticsResponse getStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Short code not found: " + shortCode));

        List<String> referrers = clickEventRepository
                .findByUrlShortCodeOrderByAccessedAtDesc(shortCode)
                .stream()
                .map(ClickEvent::getReferrer)
                .toList();

        return new AnalyticsResponse(url, referrers);
    }

    private UrlResponse toResponse(Url url, boolean created) {
        String shortUrl = baseUrl + "/" + url.getShortCode();
        return new UrlResponse(url, shortUrl, created);
    }

    private static String stripTrailingSlash(String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
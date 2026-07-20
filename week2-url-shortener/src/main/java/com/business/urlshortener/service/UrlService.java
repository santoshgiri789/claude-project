// Service layer — public contract
// Implements: REQ-SHORT-001, 002, 003, 004, 005, 006, 007, 008, 009, 010, 012.
package com.business.urlshortener.service;

import com.business.urlshortener.dto.AnalyticsResponse;
import com.business.urlshortener.dto.CreateUrlRequest;
import com.business.urlshortener.dto.UrlResponse;

public interface UrlService {

    // REQ-SHORT-001, 002, 008, 009, 010 — shorten a URL (new -> created, duplicate -> existing).
    UrlResponse shorten(CreateUrlRequest request);

    // REQ-SHORT-003, 004, 005, 007, 012 — resolve a short code, record a click, honor expiry.
    String resolveAndRecordClick(String shortCode, String referrer, String clientIp);

    // REQ-SHORT-005, 006 — return analytics for a short code.
    AnalyticsResponse getStats(String shortCode);
}

# URL Shortener — Technical Implementation Plan

**Source specification:** [`specs/url-shortener.yaml`](../specs/url-shortener.yaml) (v1.0.0)
**Stack:** Java 17, Spring Boot 3.1.3, Spring Web, Spring Data JPA, Bean Validation, H2 (in-memory)

---

## 1. Component Breakdown

| Name | Responsibility | Type |
|------|----------------|------|
| `UrlController` | Handles `POST /api/v1/urls` and `GET /api/v1/urls/{shortCode}/analytics`; maps DTOs to/from the service; returns proper status codes. | controller |
| `RedirectController` | Handles `GET /{shortCode}`; issues 302/404/410 and captures the `Referer` header. | controller |
| `UrlService` | Core business logic: validation orchestration, short-code generation, duplicate detection, expiry checks, click recording, analytics assembly. | service |
| `UrlValidator` | Validates URL syntax/scheme and blocks SSRF (private/loopback/link-local/internal hosts). | service |
| `ShortCodeGenerator` | Produces unique, URL-safe alphanumeric short codes with collision handling. | service |
| `RateLimiter` | Per-IP rate limiting for shorten and redirect endpoints; emits 429 + Retry-After. | service |
| `UrlRepository` | Spring Data JPA repository for the `Url` entity; lookups by short code and original URL. | repository |
| `ClickEventRepository` | Spring Data JPA repository for per-hit `ClickEvent` rows. | repository |
| `Url` | JPA entity for a shortened URL and its denormalized analytics aggregates. | entity |
| `ClickEvent` | JPA entity capturing an individual redirect hit (referrer, IP, timestamp). | entity |
| `UrlStatus` | Enum: `ACTIVE`, `EXPIRED`, `DELETED`. | entity |
| `CreateUrlRequest` / `UrlResponse` / `AnalyticsResponse` | Request/response DTOs with Bean Validation constraints. | dto |
| `ErrorResponse` | Structured error payload: timestamp, status, error, message. | dto |
| `GlobalExceptionHandler` | `@RestControllerAdvice` mapping domain exceptions to structured error responses. | controller |

---

## 2. Data Model

### Entity: `Url`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | PK, auto-generated |
| `shortCode` | `String` | Unique, indexed, URL-safe (REQ-SHORT-002) |
| `originalUrl` | `String` | Validated http/https URL |
| `clickCount` | `long` | Denormalized total (REQ-SHORT-005) |
| `lastAccessedAt` | `Instant` | Nullable |
| `createdAt` | `Instant` | Set on insert |
| `expiresAt` | `Instant` | Nullable = never expires (REQ-SHORT-007) |
| `status` | `UrlStatus` | ACTIVE / EXPIRED / DELETED |

**Relationships:** `Url` 1 --- * `ClickEvent` (one URL has many click events).

### Entity: `ClickEvent`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | PK, auto-generated |
| `url` | `Url` | `@ManyToOne` FK to `Url.id` |
| `referrer` | `String` | Nullable HTTP `Referer` (REQ-SHORT-005) |
| `clientIp` | `String` | Nullable |
| `accessedAt` | `Instant` | Set on insert |

---

## 3. Service Layer — Interface / Method Signatures

```java
public interface UrlService {
    // REQ-SHORT-001, 002, 008, 009, 010
    UrlResponse shorten(CreateUrlRequest request);

    // REQ-SHORT-003, 004, 005, 007, 012
    String resolveAndRecord(String shortCode, String referrer, String clientIp);

    // REQ-SHORT-006
    AnalyticsResponse getAnalytics(String shortCode);
}

public interface UrlValidator {
    // REQ-SHORT-008, 010, 012 — throws ValidationException on failure
    void validate(String url);
}

public interface ShortCodeGenerator {
    // REQ-SHORT-002 — unique, URL-safe code
    String generate();
}
```

Domain exceptions (mapped by `GlobalExceptionHandler`): `ValidationException` → 400,
`NotFoundException` → 404, `GoneException` → 410, `RateLimitExceededException` → 429.

---

## 4. Tasks

### TASK-01 — Project scaffolding & configuration
- **Implements:** (foundation for all REQ-SHORT-*)
- **Description:** Confirm `pom.xml` deps (web, data-jpa, validation, H2, test) and
  `application.properties` (in-memory H2, `/h2-console`); add the `Application` main class.
- **Acceptance criteria:**
  - `mvn clean test` runs against in-memory H2.
  - App boots and H2 console is reachable at `/h2-console`.

### TASK-02 — Domain entities & repositories
- **Implements:** REQ-SHORT-002, REQ-SHORT-005, REQ-SHORT-006, REQ-SHORT-007
- **Description:** Create `Url`, `ClickEvent`, `UrlStatus`; add `UrlRepository`
  (`findByShortCode`, `findByOriginalUrl`) and `ClickEventRepository`.
- **Acceptance criteria:**
  - `short_code` column has a unique index.
  - `expires_at` is nullable; `Url`↔`ClickEvent` relationship persists correctly.
  - Repository lookups by short code and original URL return the expected rows.

### TASK-03 — URL validation & SSRF protection
- **Implements:** REQ-SHORT-008, REQ-SHORT-010, REQ-SHORT-012
- **Description:** Implement `UrlValidator`: enforce http/https scheme and valid syntax;
  reject private, loopback, link-local, and internal-range hosts.
- **Acceptance criteria:**
  - `"not-a-valid-url"` → `ValidationException` (SCN-003).
  - `http://169.254.169.254/...` and private/loopback hosts are rejected (SCN-007).
  - Only previously validated URLs can become redirect targets (no open redirect).

### TASK-04 — Short-code generation
- **Implements:** REQ-SHORT-002
- **Description:** Implement `ShortCodeGenerator` producing URL-safe alphanumeric codes
  with collision retry against the repository.
- **Acceptance criteria:**
  - Generated codes are unique across existing rows.
  - Codes contain only URL-safe alphanumeric characters.

### TASK-05 — Shorten endpoint
- **Implements:** REQ-SHORT-001, REQ-SHORT-002, REQ-SHORT-008, REQ-SHORT-009, REQ-SHORT-010
- **Description:** Implement `UrlService.shorten` and `POST /api/v1/urls`; wire validation,
  generation, duplicate detection; return 201 for new, 200 for existing.
- **Acceptance criteria:**
  - Valid URL → 201 with `short_code` + `short_url` (SCN-001).
  - Duplicate URL → 200 with the existing short code, no new row (SCN-006).
  - Invalid/malicious URL → 400 structured error (SCN-003, SCN-007).

### TASK-06 — Redirect endpoint & click tracking
- **Implements:** REQ-SHORT-003, REQ-SHORT-004, REQ-SHORT-005, REQ-SHORT-007, REQ-SHORT-012
- **Description:** Implement `UrlService.resolveAndRecord` and `GET /{shortCode}`; increment
  `clickCount`, set `lastAccessedAt`, persist a `ClickEvent` with referrer/IP; handle expiry.
- **Acceptance criteria:**
  - Active code → 302 with `Location` = original URL; analytics updated (SCN-002).
  - Unknown code → 404 structured error (SCN-004).
  - Expired code → 410 Gone, no redirect (SCN-005).

### TASK-07 — Analytics endpoint
- **Implements:** REQ-SHORT-005, REQ-SHORT-006
- **Description:** Implement `UrlService.getAnalytics` and
  `GET /api/v1/urls/{shortCode}/analytics` returning click count, last accessed, referrers.
- **Acceptance criteria:**
  - Returns 200 with accurate `click_count`, `last_accessed_at`, and referrer data (SCN-008).
  - Unknown code → 404 structured error.

### TASK-08 — Structured error handling
- **Implements:** REQ-SHORT-004, REQ-SHORT-011
- **Description:** Add `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping domain
  exceptions to `ErrorResponse { timestamp, status, error, message }`.
- **Acceptance criteria:**
  - 400/404/410/429 all return the structured JSON body.
  - No stack traces or internal details leak in responses.

### TASK-09 — Rate limiting
- **Implements:** REQ-SHORT-011 (NFR: rate limiting)
- **Description:** Implement per-IP `RateLimiter`: 60/min for shorten, 600/min for redirect;
  return 429 with `Retry-After` on breach.
- **Acceptance criteria:**
  - Exceeding a limit → 429 with `Retry-After` header.
  - Limits are enforced per client IP and independently per endpoint.

### TASK-10 — Non-functional hardening & indexing
- **Implements:** NFR: performance, security
- **Description:** Ensure `short_code` unique index for O(1) lookups; verify error responses
  hide internals; document HTTPS-in-production expectation.
- **Acceptance criteria:**
  - Redirect lookup uses the indexed unique column (no full scan).
  - Security checks (validation, SSRF, no open redirect) are covered by tests.

### TASK-11 — Test suite
- **Implements:** all REQ-SHORT-* via SCN-001…SCN-008
- **Description:** JUnit 5 + MockMvc integration tests covering every Gherkin scenario,
  running against in-memory H2 (per `test-generator.yaml`).
- **Acceptance criteria:**
  - Every scenario SCN-001…SCN-008 has a corresponding passing test.
  - `mvn test` is green from a clean checkout.
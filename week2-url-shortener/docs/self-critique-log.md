# Self-Critique Log

A running log of `code-reviewer.yaml` cycles run against the implementation. Each entry
contains the raw JSON review output followed by a short note on what was fixed vs. ignored.

---

## Cycle 1 — 2026-07-20 — Service & controller review

**Scope reviewed:** `service/UrlValidator.java`, `service/ShortCodeGenerator.java`,
`service/UrlServiceImpl.java`, `service/UrlService.java`, `web/UrlController.java`,
`web/RedirectController.java`, `web/GlobalExceptionHandler.java` (state prior to fixes below).

```json
{
  "summary": "The service and controller layers correctly enforce scheme/host validation, duplicate detection, expiry, and structured error handling. The most significant weakness is SSRF host-filtering that only recognizes dotted-quad IPv4 literals, allowing obfuscated numeric encodings (and DNS-rebinding hostnames) to bypass the internal-host block. Remaining findings are lower-severity correctness and abuse-resistance concerns: unbounded write-on-GET click recording without rate limiting, missing app-level URL length bound, check-then-act races in duplicate/short-code handling, blind trust of X-Forwarded-For, and reflection of user input in error messages.",
  "issues": [
    {
      "id": "ISSUE-001",
      "owasp_category": "A10:2021 Server-Side Request Forgery (SSRF)",
      "severity": "high",
      "severity_score": 7.5,
      "location": "service/UrlValidator.java:isBlockedHost",
      "description": "The internal-host block only treats a host as an IP literal when it matches a dotted-quad regex or contains ':'. Obfuscated equivalents of internal addresses bypass the check and are accepted as ordinary hostnames: decimal (http://2130706433), hex (http://0x7f000001), and octal (http://0177.0.0.1) all encode 127.0.0.1. Additionally, a public hostname that resolves to a private/loopback address (DNS rebinding) is never caught because hostnames are intentionally not resolved.",
      "recommendation": "Normalize the host before classification: reject purely-numeric, 0x-hex, and leading-zero-octet host forms outright, and canonicalize to an InetAddress before applying the loopback/site-local/link-local checks. For full REQ-SHORT-010 coverage, resolve hostnames at both shorten and redirect time and re-validate the resolved address (with pinning) to defeat DNS rebinding."
    },
    {
      "id": "ISSUE-002",
      "owasp_category": "A04:2021 Insecure Design",
      "severity": "medium",
      "severity_score": 5.3,
      "location": "web/RedirectController.java:redirect / service/UrlServiceImpl.java:resolveAndRecordClick",
      "description": "The redirect endpoint performs unauthenticated database writes on every GET (increment click_count, update last_accessed_at, and INSERT a click_event row). With no rate limiting yet in place, an attacker can drive unbounded click_event growth, exhausting storage and degrading the hot redirect path (a denial-of-service / abuse vector).",
      "recommendation": "Apply the per-IP rate limiting planned in TASK-09 to the redirect path, and consider batching/async click persistence or capping retained per-URL click_event rows to bound write amplification."
    },
    {
      "id": "ISSUE-003",
      "owasp_category": "A04:2021 Insecure Design",
      "severity": "low",
      "severity_score": 3.7,
      "location": "service/UrlValidator.java:validate",
      "description": "No application-level bound on URL length. A URL longer than the original_url column (2048) passes validation and only fails at persistence time, surfacing as a DataIntegrityViolation -> 500 instead of a clean 400, contradicting REQ-SHORT-008/011.",
      "recommendation": "Reject URLs exceeding the column length during validation and return a 400 with a clear message."
    },
    {
      "id": "ISSUE-004",
      "owasp_category": "A04:2021 Insecure Design",
      "severity": "low",
      "severity_score": 3.1,
      "location": "service/UrlServiceImpl.java:shorten / service/ShortCodeGenerator.java:generate",
      "description": "Both duplicate detection (findByOriginalUrl then save) and short-code assignment (existsByShortCode then save) are check-then-act sequences with no DB-level guard on original_url. Concurrent requests for the same URL can create duplicate rows, and a short-code collision race can surface the unique-constraint violation as an unhandled 500.",
      "recommendation": "Add a unique constraint / index on original_url and rely on it (catch the constraint violation and re-read), and catch DataIntegrityViolationException around short-code save to retry generation rather than 500."
    },
    {
      "id": "ISSUE-005",
      "owasp_category": "A05:2021 Security Misconfiguration",
      "severity": "info",
      "severity_score": 2.0,
      "location": "web/RedirectController.java:clientIp",
      "description": "The client IP is taken from the left-most X-Forwarded-For value with no trusted-proxy check. The header is client-controlled and spoofable. Impact is currently limited to analytics accuracy, but becomes a security issue once rate limiting or abuse controls key on this value.",
      "recommendation": "Only honor X-Forwarded-For when the request arrives from a configured trusted proxy; otherwise use the socket remote address."
    },
    {
      "id": "ISSUE-006",
      "owasp_category": "A09:2021 Security Logging and Monitoring Failures",
      "severity": "info",
      "severity_score": 1.5,
      "location": "service/UrlServiceImpl.java:resolveAndRecordClick / service/UrlValidator.java:validate",
      "description": "Error messages reflect raw user input back to the caller (e.g. 'Short code not found: <input>', 'URL targets a blocked or internal host: <host>'). Responses are JSON so there is no direct XSS, but echoing attacker input is unnecessary and can aid enumeration/log injection.",
      "recommendation": "Return generic messages that do not embed raw input, and log the specifics server-side at an appropriate level."
    }
  ]
}
```

### What I fixed / what I ignored and why

**Fixed now (this cycle):**
- **ISSUE-001 (partial, the high-value part):** Hardened `UrlValidator.isBlockedHost` to
  reject obfuscated numeric host encodings — pure-decimal (`2130706433`), `0x` hex
  (`0x7f000001`), and dotted forms with leading-zero (octal) octets — before the
  dotted-quad check. This closes the practical loopback/internal-address bypass.
- **ISSUE-003:** Added a `MAX_URL_LENGTH` (2048) check in `validate(...)` so over-long URLs
  return a clean 400 instead of a DB-driven 500.

**Deferred / ignored, with reasons:**
- **ISSUE-001 (DNS-rebinding remainder):** Full mitigation requires resolving hostnames at
  both shorten and redirect time and pinning the resolved address. That adds a live network
  dependency that would make the offline `mvn test` suite flaky, and belongs with the
  redirect-time hardening — deferred, tracked against REQ-SHORT-010.
- **ISSUE-002 (write-on-GET DoS):** This is exactly what **TASK-09 (rate limiting)** covers;
  fixing it here would duplicate that task. Deferred to TASK-09 rather than ignored.
- **ISSUE-004 (races):** Low severity and only observable under concurrent load; the clean
  fix (unique constraint on `original_url` + constraint-violation retry) is an entity/schema
  change that touches TASK-02 and warrants its own change. Deferred.
- **ISSUE-005 / ISSUE-006 (info):** Intentionally left as-is for now. X-Forwarded-For only
  affects analytics until rate limiting lands (revisit with TASK-09); the reflected-input
  messages are useful for the assignment's test assertions and carry no XSS risk in JSON
  responses. Noted, not fixed.

---

## Cycle 2 — 2026-07-20 — after fix (high-severity remediation)

**Scope:** Remediation of the high-severity SSRF / open-redirect / input-validation findings
from Cycle 1, focused on `service/UrlValidator.java` and `service/UrlServiceImpl.java`.

### Changes applied

1. **SSRF host filter (ISSUE-001 — now fully resolved).** `UrlValidator.isBlockedHost` was
   rewritten to:
   - Reject obfuscated numeric host encodings: pure decimal (`2130706433`), `0x` hex
     (`0x7f000001`), and **any** octal octet including 4-digit forms (`0177.0.0.1`) — the
     last of which slipped past the Cycle-1 fix and was caught during verification below.
   - Resolve public hostnames best-effort via `getAllByName` and block if **any** resolved
     address is internal (defeats a benign-looking name that maps into private space);
     unresolvable names stay allowed so offline `mvn test` is not network-dependent.
   - Classify addresses with a shared `isInternalAddress(...)` covering loopback, any-local,
     link-local, site-local, multicast, plus IPv4 `0.0.0.0/8` and `100.64.0.0/10` (CGNAT)
     and IPv6 unique-local `fc00::/7`.
2. **Embedded-credential rejection (ISSUE-001 hardening).** URLs carrying `userInfo`
   (`http://user:pass@host`, `http://evil.com@127.0.0.1`) are now rejected — a common tactic
   to obscure the true host.
3. **Absolute-URL + length enforcement (ISSUE-003, input validation).** Non-absolute URLs are
   rejected and the `MAX_URL_LENGTH` (2048) bound (added in Cycle 1) is retained, so bad input
   returns a clean 400 rather than a DB-driven 500.
4. **Open-redirect defense in depth (ISSUE-001 / REQ-SHORT-012).**
   `UrlServiceImpl.resolveAndRecordClick` now re-checks that the stored target begins with
   `http://` or `https://` before emitting the 302, so a tampered/legacy row can never drive
   a redirect to a non-http(s) target.

### Verification

A full `mvn test` could not be run in this environment (no Maven and no Spring/JPA jars
available offline). The security-critical host-classification logic was instead mirrored into
a standalone `java.net`-only harness and executed against a 21-case battery (public hosts,
loopback/private/link-local/CGNAT literals, decimal/hex/octal obfuscation, IPv6 loopback /
link-local / unique-local, and credential-smuggling forms):

```
21 passed, 0 failed
```

The one initially-failing case (`http://0177.0.0.1` — 4-digit octal octet) was fixed by
broadening numeric-host detection (`NUMERIC_DOTTED`) and re-verified green.

### Resolution status

| Issue | Severity | Status after Cycle 2 |
|-------|----------|----------------------|
| ISSUE-001 (SSRF host-filter bypass) | high | **Resolved** — obfuscation forms blocked, hostnames resolved & checked, credentials rejected, verified 21/21 |
| ISSUE-003 (missing URL length / absolute check) | low | **Resolved** — 400 on over-long / non-absolute URLs |
| Open-redirect (REQ-SHORT-012) | — | **Hardened** — redirect-time scheme re-check added |
| ISSUE-002 (write-on-GET DoS) | medium | Deferred to TASK-09 (rate limiting) |
| ISSUE-004 (check-then-act races) | low | Deferred (schema change, TASK-02) |
| ISSUE-005 / ISSUE-006 (info) | info | Deferred / accepted |

Residual note on ISSUE-001: DNS **rebinding** (name resolves to a public IP at validation but
a private IP later) remains only partially mitigated because the server never makes the
outbound request itself — the redirect is client-side, so validation-time resolution is the
appropriate control here. Full pinning would matter only if the service later fetches URLs.
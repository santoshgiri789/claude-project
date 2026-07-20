# Schema-Enforced Output

This document captures how the `code-reviewer.yaml` prompt constrained the model's output
to a fixed, machine-checkable JSON structure during the self-critique step.

## 1. The JSON schema used

Copied verbatim from `prompts/code-reviewer.yaml` (`output_schema.json_schema`):

```yaml
type: object
properties:
  summary: { type: string }
  issues:
    type: array
    items:
      type: object
      properties:
        id: { type: string }
        owasp_category: { type: string }
        severity: { type: string, enum: [critical, high, medium, low, info] }
        severity_score: { type: number }
        location: { type: string }
        description: { type: string }
        recommendation: { type: string }
      required: [id, severity, description, recommendation]
required: [summary, issues]
```

## 2. The actual JSON returned (Cycle 1, pretty-printed)

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

## 3. Why the schema mattered

Because the prompt declared an explicit `json_schema` with `required: [id, severity,
description, recommendation]` and a bounded `severity` enum, the model could not answer with
free-form prose — every finding had to arrive as a discrete object carrying a stable
identifier, a normalized severity drawn from a fixed set, and an actionable recommendation.
That constraint turned the review into data rather than narrative: the output can be parsed,
diffed between cycles (Cycle 1 -> Cycle 2), counted and sorted by severity, checked in CI for
"no unresolved high/critical issues," and cross-referenced back to `REQ-SHORT-NNN` IDs — none
of which would be reliable against an unstructured paragraph where fields might be missing,
renamed, or buried in sentences.
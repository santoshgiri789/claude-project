# Week 2 — Spec-Driven Feature Factory: Written Report

Project: **URL Shortener** (Java 17 / Spring Boot 3.1.3 / H2). This report answers the 12
graded questions in Part 5. Evidence lives in `specs/`, `docs/`, `prompts/`, and `src/`.

> **Two honest caveats up front (the assignment rewards this):**
> 1. `mvn test` was **not executed** in the environment these artifacts were produced in
>    (no Maven / offline). So Q10's first-run pass rate and the traceability "pass/fail"
>    column are marked **PENDING** — fill them from a real `./mvnw test` run before submitting.
> 2. Q12's time breakdown uses **your** timer notes — placeholders are marked `<fill in>`.

---

## Quick-reference answer map

| Q | Topic | Primary evidence |
|---|-------|------------------|
| Q1 | Spec-first vs one-liner | `specs/url-shortener.yaml` |
| Q2 | YAML templates vs ad-hoc | `prompts/*.yaml` |
| Q3 | Self-critique loop | `docs/self-critique-log.md` |
| Q4 | Traceability completeness | `docs/traceability-matrix.md` |
| Q5 | Mermaid diagrams | `specs/diagrams/*.md` |
| Q6 | Mid-sprint change | this report |
| Q7 | Best YAML template | `prompts/code-reviewer.yaml` |
| Q8 | Schema-enforced output | `docs/schema-enforced-output.md` |
| Q9 | Traceability matrix + count | `docs/traceability-matrix.md` |
| Q10 | First-run pass rate | **PENDING real run** |
| Q11 | Gherkin → test | `specs/url-shortener.yaml` + `UrlShortenerIntegrationTest` |
| Q12 | Time breakdown | your timer notes |

---

## Q1 — Did spec-first improve quality vs "build me a URL shortener"?

Yes, materially. A one-line prompt ("build me a URL shortener") typically yields a single
controller that stores a map and redirects — no validation, no expiry, no analytics schema,
no error contract, and (critically for this feature) **no SSRF protection**. Because we wrote
the spec first, the implementation was forced to satisfy **12 explicit requirements**
(`REQ-SHORT-001…012`) including duplicate detection, optional expiry with a `410 Gone` path,
per-hit referrer history, structured JSON errors, and blocking of localhost/private/internal
hosts. The spec turned vague intent into a checklist the code and tests could both be measured
against — the whole point of SDD.

## Q2 — Value of YAML prompt templates vs ad-hoc prompting?

- **Reusability & consistency:** the same `spec-writer`/`architect`/`code-reviewer`/
  `test-generator` recipe produces the same *shape* of output every time, instead of drifting
  with each hand-typed prompt.
- **Versioning:** every template has a `version` field, so prompt changes are trackable like code.
- **Role separation:** each file pins a distinct persona (`role:`) — security reviewer vs QA
  engineer — which measurably changes output focus.
- **Schema enforcement:** `output_schema` makes the output machine-checkable (see Q8).
- **Onboarding:** a new teammate reads four small YAML files instead of reverse-engineering
  someone's chat history.

## Q3 — Self-critique loop: what did the review catch, and what did it miss?

From `docs/self-critique-log.md` (2 cycles):

**Real issues caught (high value):**
- **ISSUE-001 (high, SSRF):** the first validator only recognized dotted-quad IPv4 literals, so
  obfuscated encodings of `127.0.0.1` — decimal `2130706433`, hex `0x7f000001`, octal
  `0177.0.0.1` — and private-resolving hostnames bypassed the internal-host block.
- **ISSUE-003 (low):** no app-level URL length bound → a too-long URL failed at the DB as a 500
  instead of a clean 400.
- Plus medium/low/info findings: write-on-GET DoS, check-then-act races, `X-Forwarded-For`
  trust, reflected input in errors.

**What the review missed (honest):** the AI review did *not* catch that even my Cycle-1 fix
still let a **4-digit octal octet** (`0177.0.0.1`) through — that was caught by a separate
standalone verification harness I ran, not by the review itself. Also, the **race condition**
on duplicate/short-code creation was correctly *identified* but consciously *deferred*, so it
remains a latent bug. Lesson: an AI self-review is good at breadth but still needs an
independent executable check to confirm a fix actually holds.

## Q4 — How complete was traceability? Any gaps?

See `docs/traceability-matrix.md`: **12 of 12 requirements** have both implementing code (with
`// REQ-SHORT-NNN` comments) and at least one test. No requirement is untested. Two are flagged
as **weakly covered**: `REQ-SHORT-002` (short code returned, but uniqueness/charset not asserted
directly) and `REQ-SHORT-012` (open-redirect only covered transitively via the SSRF test). One
test (`contextLoads`) is intentionally not tied to a requirement.

## Q5 — Role of the Mermaid diagrams: did they reveal a missed requirement?

Yes — concretely, the **state diagram** (`specs/diagrams/state.md`) forced the question "what
is a URL between *expired* and *deleted*?" That drove the decision to use a **`UrlStatus` flag
(ACTIVE → EXPIRED → DELETED)** and a soft-delete model rather than hard-deleting rows, so that
an expired link returns `410 Gone` (not a `404`) and analytics survive expiry. The **ER
diagram** similarly pushed me to split `ClickEvent` into its own table (per-hit referrer
history) instead of only keeping a denormalized counter on `Url`.

## Q6 — If the PM adds "password protection" mid-sprint?

The SDD-disciplined response, not a code hack:
1. **Update the spec first** — add `REQ-SHORT-013` (e.g. "a short URL MAY require a passphrase;
   the redirect SHALL return `401` until the correct passphrase is supplied") plus a new Gherkin
   scenario and an API-contract change (`POST` accepts an optional `password`; redirect accepts
   a challenge).
2. **Impact analysis** — the new REQ touches `Url` (a `passwordHash` column), `UrlService`
   (verify), `RedirectController` (challenge flow), and DTOs. Note it in the plan.
3. **Regenerate/extend tests** from the updated spec (happy path + wrong-password + no-password).
4. **Re-run traceability** so `REQ-SHORT-013` shows code + test coverage before merging.

The key is spec → tests → code stay in sync; the spec remains the single source of truth.

## Q7 — Best YAML template, with each field explained

`prompts/code-reviewer.yaml` — strongest because it demonstrates real JSON-schema enforcement.

```yaml
name: code-reviewer          # stable identifier for the template
version: 1.0.0               # lets prompt changes be versioned like code
role: >                     # pins the persona — "senior application security reviewer"
  ... audits against OWASP Top 10 ...
task: >                     # the instruction + {{ code }} placeholder for the input
  ... report every security/correctness issue with id, OWASP category, severity, fix ...
output_schema:              # forces the shape of the answer
  format: json
  json_schema:              # an actual JSON Schema: typed fields + required[] + severity enum
    ...
tags: [security, owasp, review, self-critique]   # discoverability / categorization
```

- **name** — how you refer to and reuse the template.
- **version** — track and diff prompt evolution.
- **role** — sets Claude's expertise lens (security vs QA) which shifts what it notices.
- **task** — the concrete instruction, parameterized with `{{ code }}`.
- **output_schema** — the enforcement contract; here a `json_schema` with a bounded `severity`
  enum and `required: [id, severity, description, recommendation]`.
- **tags** — metadata for organizing a growing prompt library.

## Q8 — The JSON schema and the validated output

See `docs/schema-enforced-output.md`, which contains (1) the exact `json_schema` copied from
`code-reviewer.yaml`, (2) the actual pretty-printed JSON the review returned (6 issues,
`ISSUE-001…006`), and (3) a note on why the schema mattered. Because the schema declared
`required: [id, severity, description, recommendation]` and a fixed `severity` enum, the review
came back as **parseable data** — sortable by severity, diffable between cycles, and CI-checkable
for "no unresolved high/critical" — instead of free-form prose.

## Q9 — Traceability matrix and coverage count

Full table in `docs/traceability-matrix.md`. Summary: **12 of 12 requirements have full
code+test coverage.** (Status column currently **PENDING** an actual `mvn test` run — see Q10.)

## Q10 — % of auto-generated tests passing on the first run + failure types

**PENDING — record from a real run before submitting.** `mvn test` was not executed in the
environment where these files were generated (no Maven available), so I will not fabricate a
number. To fill this in honestly:

```
cd week2-url-shortener && ./mvnw test
```

Then report: total tests run, number passing on the *first* run, and the failure categories.
Based on a static review, the most likely first-run issues to watch for are: (a) JSON field
casing — the app was set to `SNAKE_CASE` to match the spec's contract, so any assertion
expecting camelCase would fail; (b) the `Location` header exact-match in `scn002` depending on
`app.base-url`; (c) `scn008`'s `referrers.length()` counting `null` entries for hits with no
`Referer`. Suite size: **9 scenario/edge tests + 1 context-load test = 10 tests.**

> _Example of how to word it once you have the numbers:_ "10 tests ran; 8 passed on the first
> run; 2 failed — one casing mismatch and one Location assertion — both fixed by [change]."

## Q11 — A Gherkin scenario and the test derived from it

**Scenario (from `specs/url-shortener.yaml`):**

```
SCN-005 — Expired URL is gone (edge case)
  given: a short code "old123" exists but its expiration date is in the past
  when:  the client sends GET /old123
  then:  the service responds 410 Gone and does not redirect (REQ-SHORT-007).
```

**Matching test (`UrlShortenerIntegrationTest`):**

```java
// SCN-005 — Expired URL is gone (edge case)  [REQ-SHORT-007]
@Test
void scn005_expiredUrl_returns410() throws Exception {
    // Scenario SCN-005 verifies REQ-SHORT-007 (expired short URL -> 410 Gone).
    Url expired = new Url("old123", "https://www.example.com",
            Instant.now().minus(1, ChronoUnit.DAYS));
    urlRepository.save(expired);

    mockMvc.perform(get("/{code}", "old123"))
            .andExpect(status().isGone());
}
```

## Q12 — Time breakdown per part; the longest and why

| Part | Task | Time |
|------|------|------|
| Setup | Folders + `pom.xml` + `application.properties` | `<fill in>` |
| Part 1 | 4 YAML prompt templates | `<fill in>` |
| Part 2 | Spec + 3 Mermaid diagrams | `<fill in>` |
| Part 3 | Plan + implementation + 2 self-critique cycles | `<fill in>` |
| Part 4 | Test generation + traceability matrix | `<fill in>` |
| Report | This document | `<fill in>` |

**Longest part (expected):** Part 3 — it covered the architecture plan, entities/repositories,
the service layer with SSRF/validation logic, controllers + global exception handler, and two
rounds of security self-critique with fixes. It has the most moving parts and the only step
that required iterating on a real bug (the SSRF encoding bypass). _Replace with your actual
timer notes; if a different part took longest for you, say so and why._
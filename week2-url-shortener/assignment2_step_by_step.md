# Week 2 Assignment — The Spec-Driven Feature Factory

> **Your reference guide.** Read this top-to-bottom once, then follow it step by step.
> Every step tells you **what** you're doing and **why** in plain language, and gives you the
> **exact prompt** to paste into Claude Code where one is needed.

---

## 0. The Big Picture (read this first)

**What the assignment really is:** Instead of jumping straight into coding a URL Shortener, you
first write a *formal specification* (a precise document describing exactly what the software must
do), then let Claude generate the code **from that spec**, then auto-generate tests **from that
same spec**, and finally prove that every requirement is covered by code and a test.

**Why they make you do it this way (Spec-Driven Development / SDD):** In real teams, most bugs come
from misunderstood requirements, not bad typing. If you nail down *what* to build in a precise,
reviewable document first, the AI (and humans) produce far better, more consistent code. This
assignment is teaching you that discipline.

**The 5 skills being graded:**
1. **Spec-driven development** — spec before code.
2. **YAML prompt management** — reusable, versioned prompt templates (not ad-hoc typing).
3. **Schema enforcement** — forcing Claude to return output in a strict structure (JSON schema).
4. **Role-based prompting** — telling Claude "you are a security reviewer" vs "you are a QA engineer".
5. **Self-critique loops** — Generate code → Review it → Fix → Validate.

**The feature you're building:** A **URL Shortener** — turns a long URL into a short code like
`abc123`, redirects when someone visits the short link, and tracks clicks/analytics, with expiry
and validation.

**Your chosen setup:** Java + Spring Boot, built as a self-contained project inside
`week2-url-shortener/` in this repo. We use **H2 (in-memory database)** so the grader (and you) can
run tests with zero database setup.

---

## 1. Deliverables Checklist (what you must submit)

A GitHub repo (or subfolder) containing:

| # | Deliverable | Folder/File |
|---|-------------|-------------|
| 1 | 4 YAML prompt templates | `prompts/spec-writer.yaml`, `prompts/architect.yaml`, `prompts/code-reviewer.yaml`, `prompts/test-generator.yaml` |
| 2 | Full structured spec | `specs/url-shortener.yaml` |
| 3 | Mermaid diagrams (sequence, ER, state) | `specs/diagrams/` |
| 4 | Implementation with `// REQ-...` comments | `src/` |
| 5 | Auto-generated tests | `src/test/` (Maven convention) |
| 6 | Traceability matrix | `docs/traceability-matrix.md` |
| 7 | Self-critique log | `docs/self-critique-log.md` |
| 8 | Written report (12 questions) | `REPORT.md` |

> **Tip:** Keep a running note of **time spent per part** and **the pass/fail of your first test
> run** — you need both for the report (Q10, Q12). Start a timer now.

---

## 2. Suggested Time Budget (4–5 hours)

| Part | Task | Target time |
|------|------|-------------|
| Setup | Create folder + scaffold project | 15 min |
| Part 1 | 4 YAML prompt templates | 45 min |
| Part 2 | Spec + Mermaid diagrams | 45 min |
| Part 3 | Plan + implement + self-critique | 90 min |
| Part 4 | Generate tests + traceability matrix | 45 min |
| Report | Answer the 12 questions | 40 min |

---

## SETUP — Create the project skeleton (15 min)

**What:** Make the folder structure and a minimal Spring Boot project.
**Why:** Everything else drops into these folders. Having the skeleton first means the spec, code,
and tests all have a home.

### Step S1 — Create the folders

Paste this to Claude Code (or run it yourself in the terminal):

```
Create this folder structure inside week2-url-shortener/ in the current repo:

week2-url-shortener/
├── prompts/
├── specs/diagrams/
├── docs/
├── src/main/java/com/business/urlshortener/
├── src/main/resources/
└── src/test/java/com/business/urlshortener/

Do not add any code yet — just create the empty folders with .gitkeep files where needed.
```

### Step S2 — Create the Maven project file

**What:** A `pom.xml` that pulls in Spring Web (REST), JPA (database), Validation, and H2.
**Why:** H2 is an in-memory database — no MySQL install, tests "just run". This keeps the assignment
self-contained and easy to grade.

Paste to Claude Code:

```
Create week2-url-shortener/pom.xml for a standalone Spring Boot 3.1.3 / Java 17 Maven project.
- groupId: com.business
- artifactId: url-shortener
- Dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa,
  spring-boot-starter-validation, com.h2database:h2 (runtime),
  spring-boot-starter-test (test scope).
- Include the spring-boot-maven-plugin.
Also create src/main/resources/application.properties configured for an in-memory H2 database
with the H2 console enabled at /h2-console.
```

### Step S3 — Sanity check it builds

Run:

```
cd week2-url-shortener && ./mvnw -q compile
```

> If `mvnw` isn't in the subfolder, copy `mvnw`, `mvnw.cmd`, and the `.mvn/` folder from the repo
> root, or just use a globally installed `mvn`. Ask Claude: *"Copy the Maven wrapper files from the
> repo root into week2-url-shortener/."*

✅ **Done when:** `mvnw compile` succeeds (there's no real code yet, so it should be instant).

---

## PART 1 — YAML Prompt Library (45 min)

**What:** Write 4 reusable prompt "templates" as YAML files. Each one is a saved, structured recipe
that turns Claude into a specific expert (spec writer, architect, reviewer, tester).
**Why:** Instead of re-typing long prompts each time (and getting different results), you save them
once, version them, and reuse them. This is exactly how mature teams manage prompts. Graded at 20%.

Each template **must** contain these fields: `name`, `version`, `role`, `task`, `output_schema`,
`tags`. Use `{{ variable }}` placeholders so the same template works for different inputs.

> **How you'll "use" these templates:** Claude Code doesn't auto-load YAML prompts. You *use* a
> template by opening it and telling Claude: *"Follow the instructions in prompts/spec-writer.yaml,
> using feature_request = (the PM request)."* The YAML is your reusable script; you feed it in.

### Step 1.1 — Create `prompts/spec-writer.yaml`

Paste to Claude Code:

```
Create week2-url-shortener/prompts/spec-writer.yaml exactly as below:

name: spec-writer
version: 1.0.0
role: >
  You are a senior product analyst and requirements engineer. You convert informal
  feature requests into formal, testable software specifications.
task: >
  Convert the following feature request into a formal specification.
  Requirements:
  - Use SHALL/MUST normative language for every requirement.
  - Assign each requirement a unique ID in the form REQ-SHORT-NNN.
  - Include at least 6 Gherkin scenarios (Given/When/Then) covering the happy path,
    error cases, and edge cases.
  - Include an OpenAPI-style contract for every endpoint (method, path, request, responses).
  - Include non-functional requirements: performance, security, and rate limiting.
  Feature request:
  {{ feature_request }}
output_schema:
  format: yaml
  structure:
    metadata: { title, version, author }
    functional_requirements: [ { id, statement, priority } ]
    gherkin_scenarios: [ { id, title, given, when, then } ]
    api_contract: [ { method, path, request, responses } ]
    non_functional_requirements: { performance, security, rate_limiting }
tags: [spec, requirements, gherkin, openapi, sdd]
```

### Step 1.2 — Create `prompts/architect.yaml`

```
Create week2-url-shortener/prompts/architect.yaml exactly as below:

name: architect
version: 1.0.0
role: >
  You are a senior software architect. You turn specifications into concrete, buildable
  implementation plans for a Java 17 / Spring Boot 3 application.
task: >
  Read the specification and produce a technical implementation plan.
  Requirements:
  - Break the work into numbered tasks (TASK-01, TASK-02, ...).
  - Each task MUST reference the spec requirement IDs it implements (e.g. REQ-SHORT-001).
  - Each task MUST list acceptance criteria.
  - Provide a component breakdown (controllers, services, repositories, entities, DTOs).
  - Define the data model (entities, fields, types, relationships).
  - Define interface/method signatures for the service layer.
  Specification:
  {{ specification }}
output_schema:
  format: markdown
  structure:
    components: [ { name, responsibility, type } ]
    data_model: [ { entity, fields, relationships } ]
    tasks: [ { id, description, requirement_ids, acceptance_criteria } ]
tags: [architecture, planning, spring-boot, design]
```

### Step 1.3 — Create `prompts/code-reviewer.yaml`

```
Create week2-url-shortener/prompts/code-reviewer.yaml exactly as below:

name: code-reviewer
version: 1.0.0
role: >
  You are a senior application security reviewer. You audit code against the OWASP Top 10
  and secure-coding best practices.
task: >
  Review the provided code and report every security or correctness issue you find.
  For each issue provide: an id, the OWASP category, a severity score, the file/line,
  a description, and a concrete fix recommendation.
  Focus on: input validation, injection (SQL/SSRF), open redirects, malicious URL handling,
  authentication/authorization, error handling, and information leakage.
  Code to review:
  {{ code }}
output_schema:
  format: json
  json_schema:
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
tags: [security, owasp, review, self-critique]
```

### Step 1.4 — Create `prompts/test-generator.yaml`

```
Create week2-url-shortener/prompts/test-generator.yaml exactly as below:

name: test-generator
version: 1.0.0
role: >
  You are a senior QA automation engineer. You write comprehensive, runnable tests
  using JUnit 5 and Spring Boot Test (MockMvc) for Java 17 / Spring Boot 3.
task: >
  Generate a test suite from the specification.
  Requirements:
  - Cover EVERY Gherkin scenario in the spec (happy path, errors, edge cases).
  - Add a comment on each test naming the scenario ID and requirement ID it verifies.
  - Include edge cases: invalid URLs, duplicate URLs, expired URLs, missing fields,
    malicious/SSRF URLs, and not-found short codes.
  - Tests must compile and run with `mvn test` against an in-memory H2 database.
  Specification:
  {{ specification }}
output_schema:
  format: java
  structure:
    test_classes: [ { class_name, scenarios_covered, requirement_ids } ]
tags: [testing, qa, junit, gherkin, coverage]
```

✅ **Done when:** all 4 files exist in `prompts/`. Commit now: *"Part 1: YAML prompt library."*

> **Report note:** Pick your favorite of these 4 for **Q7** and be ready to explain each field.
> `code-reviewer.yaml` is a strong choice because its `output_schema` shows real JSON-schema
> enforcement.

---

## PART 2 — Structured Specification (45 min)

**What:** Use your `spec-writer.yaml` template to generate the formal spec, then generate 3 diagrams.
**Why:** This is the heart of SDD — the single source of truth everything else is built and tested
against. Graded at 25%.

### Step 2.1 — Generate the spec

Paste to Claude Code:

```
Follow the instructions in week2-url-shortener/prompts/spec-writer.yaml.
Set {{ feature_request }} to this PM request:

"Build a URL Shortener Service:
- Core: Shorten long URLs into unique short codes (e.g., abc123)
- Redirect: Visiting the short URL redirects to the original
- Analytics: Track click count, last accessed timestamp, referrer
- Expiry: URLs can have optional expiration dates
- Validation: Reject invalid URLs, duplicates, and malicious links
- API: RESTful API with proper error responses"

Write the result to week2-url-shortener/specs/url-shortener.yaml.
It must include: SHALL/MUST statements with REQ-SHORT-NNN IDs, at least 6 Gherkin
scenarios (happy + error + edge), an OpenAPI-style contract for all endpoints, and
non-functional requirements (performance, security, rate limiting).
```

**After it generates, review it yourself.** Check that:
- Every requirement has an ID (`REQ-SHORT-001`, `002`, ...) and says SHALL or MUST.
- There are **≥ 6** Gherkin scenarios and they include errors (invalid URL, duplicate, expired,
  not found) — not just the happy path.
- There's an endpoint for: create short URL (`POST /api/urls`), redirect (`GET /{code}`), and get
  analytics (`GET /api/urls/{code}/stats`).

### Step 2.2 — Generate the Mermaid diagrams

**What:** Three diagrams — a sequence diagram (how a request flows), an ER diagram (the data
tables), and a state diagram (a URL's life: active → expired → deleted).
**Why:** Drawing forces you to spot gaps the text spec hides — e.g., "what state is a URL in after
it expires but before it's deleted?" That's the point of **Q5** in the report.

Paste to Claude Code:

```
Generate three Mermaid diagrams for the URL shortener based on
week2-url-shortener/specs/url-shortener.yaml:

1. A sequence diagram of the shorten-then-redirect flow (Client, Controller, Service,
   Repository, Database). Save as specs/diagrams/sequence.md.
2. An ER diagram of the data model (the URL entity and any click/analytics records).
   Save as specs/diagrams/er.md.
3. A state diagram of the URL lifecycle: active -> expired -> deleted (include the events
   that cause each transition). Save as specs/diagrams/state.md.

Wrap each diagram in a ```mermaid code block so it renders on GitHub.
Then add a "## Diagrams" section at the bottom of specs/url-shortener.yaml that links to
the three files.
```

✅ **Done when:** `specs/url-shortener.yaml` + 3 diagram files exist. Preview the diagrams on
GitHub (or VS Code Mermaid preview) to confirm they render. Commit: *"Part 2: spec + diagrams."*

> **Report note (Q5):** Write down one thing a diagram made you realize you'd missed (e.g., "the
> state diagram made me add a `deleted` status flag instead of hard-deleting rows"). You need a
> concrete example.

---

## PART 3 — Spec-Driven Implementation (90 min)

**What:** Turn the spec into a plan (`architect.yaml`), then build it task-by-task, running a
**self-critique loop** with `code-reviewer.yaml`. This is the biggest part. Graded at 25%.
**Why:** This proves you can go from spec → working code with full traceability (every file cites
its requirement ID) and that you used AI to review its own output.

### Step 3.1 — Generate the implementation plan

Paste to Claude Code:

```
Follow the instructions in week2-url-shortener/prompts/architect.yaml.
Set {{ specification }} to the full contents of week2-url-shortener/specs/url-shortener.yaml.
Write the plan to week2-url-shortener/docs/implementation-plan.md.
Every task must reference the REQ-SHORT-NNN IDs it implements and list acceptance criteria.
```

Read the plan. It should produce a component list roughly like:
- `ShortUrl` entity (JPA) + `ClickEvent` (or analytics fields)
- `UrlRepository` (Spring Data JPA)
- `UrlService` (shorten, resolve, record click, validate)
- `UrlController` (REST endpoints) + `RedirectController`
- DTOs + a global exception handler for clean error responses

### Step 3.2 — Implement task-by-task (with traceability)

**What:** Build the code following the plan. **Every source file must carry a comment linking it to
the spec**, like `// REQ-SHORT-001`.
**Why:** Traceability = you can point at any line of code and say which requirement it satisfies.
Graders look for these comments.

Do this in **small chunks** (don't ask for the whole app at once — you get better, reviewable code).
Example prompts, one per layer:

```
Using week2-url-shortener/docs/implementation-plan.md and specs/url-shortener.yaml,
implement TASK-01 and TASK-02: the JPA entity/entities and the repository, under
package com.business.urlshortener. At the top of each file add a comment listing the
REQ-SHORT-NNN IDs it implements. Use H2-compatible JPA annotations.
```

```
Now implement the service layer (shorten, resolve-and-record-click, get-stats, validation).
Include: reject invalid URLs, reject duplicates (return the existing code), reject malicious
URLs (block non-http(s) schemes, localhost/private IPs to prevent SSRF), and honor expiry.
Add REQ-SHORT-NNN comments on each method. Follow the spec exactly.
```

```
Now implement the REST controller(s) and a @RestControllerAdvice global exception handler
that returns proper JSON error responses with correct HTTP status codes, matching the
OpenAPI contract in the spec. Add REQ-SHORT-NNN comments.
```

After each chunk, verify it compiles: `cd week2-url-shortener && ./mvnw -q compile`.

### Step 3.3 — The self-critique loop (Generate → Review → Fix → Validate)

**What:** Feed your freshly written code to `code-reviewer.yaml`, get a JSON list of issues, fix the
real ones, and re-check. Log the whole thing.
**Why:** This is a graded, named skill. It also genuinely improves the code. **Q3** asks what the
review caught and what it missed.

Paste to Claude Code:

```
Follow the instructions in week2-url-shortener/prompts/code-reviewer.yaml.
Set {{ code }} to the current contents of the service and controller classes in
week2-url-shortener/src/main/java/com/business/urlshortener/.
Return the result strictly as JSON per the template's json_schema.
Then append the full JSON, plus a short "what I fixed / what I ignored and why" note,
to week2-url-shortener/docs/self-critique-log.md as a new dated cycle entry.
```

Then, for the real issues:

```
Apply fixes for the high/critical issues from the last review (especially SSRF / open-redirect
/ input validation). After fixing, recompile and append a "Cycle 2 — after fix" entry to
docs/self-critique-log.md noting which issues are now resolved.
```

Repeat until no high/critical issues remain (usually 1–3 cycles is plenty).

### Step 3.4 — Schema-enforced output (required, worth documenting)

**What:** At least one Claude interaction must **force a strict JSON schema** on the output. Your
`code-reviewer.yaml` already does this (its `output_schema.json_schema`). Capture proof.
**Why:** The assignment explicitly requires using `--json-schema` or an inline schema, and **Q8**
asks you to show the schema and the actual validated response.

Do this:

```
Create week2-url-shortener/docs/schema-enforced-output.md containing:
1. The exact JSON schema used (copy it from code-reviewer.yaml's output_schema.json_schema).
2. The actual JSON Claude returned in the self-critique step, pretty-printed.
3. A one-paragraph note explaining that the schema forced a consistent, machine-checkable
   structure (id, severity, recommendation) instead of free-form prose.
```

> **Optional flex:** Claude Code's CLI supports enforcing output shape. If you run a headless call,
> you can pass a schema, e.g.:
> `claude -p "review this file" --output-format json` and validate the result. Document whichever
> method you used. The inline `json_schema` in your YAML fully satisfies the requirement.

✅ **Done when:** app compiles, self-critique log has ≥2 cycles, `schema-enforced-output.md` exists.
Commit: *"Part 3: implementation + self-critique."*

---

## PART 4 — Test Generation & Validation (45 min)

**What:** Auto-generate tests from the spec with `test-generator.yaml`, run them, record what passed
on the **first run**, fix failures, and build the traceability matrix. Graded at 15%.
**Why:** Tests derived from the spec prove the code does what the spec said. The **first-run pass
rate** is a key SDD metric (**Q10**), so record it honestly before you fix anything.

### Step 4.1 — Generate the tests

Paste to Claude Code:

```
Follow the instructions in week2-url-shortener/prompts/test-generator.yaml.
Set {{ specification }} to the full contents of week2-url-shortener/specs/url-shortener.yaml.
Generate JUnit 5 + Spring Boot MockMvc tests under
week2-url-shortener/src/test/java/com/business/urlshortener/.
There must be at least one test per Gherkin scenario in the spec. On each test method, add a
comment naming the Gherkin scenario ID and the REQ-SHORT-NNN it verifies. Tests must run
against in-memory H2 with `mvn test`.
```

### Step 4.2 — Run them and record the FIRST result

```
cd week2-url-shortener && ./mvnw test
```

**Immediately write down** (for Q10): how many tests ran, how many passed on this very first run,
and what the failures were (compile error? wrong status code? missing bean?). Don't fix yet — just
record. Then:

```
Some tests failed on the first run. Here is the Maven output: <paste the failing output>.
Fix the code (not the tests, unless a test contradicts the spec) so the tests pass. If a test
is wrong per the spec, fix the test and note it. Re-run mvn test until green. Append an
iteration note to docs/self-critique-log.md describing what failed and what you changed.
```

### Step 4.3 — Build the traceability matrix

**What:** A table mapping every requirement → the code file that implements it → the test that
verifies it → pass/fail.
**Why:** It exposes gaps: a requirement with no test = untested = likely bug in production. **Q4**
and **Q9** are about this.

Paste to Claude Code:

```
Create week2-url-shortener/docs/traceability-matrix.md as a Markdown table with columns:
Requirement ID | Requirement (short) | Code File | Test File / Method | Status (pass/fail).
Fill it by scanning the REQ-SHORT-NNN comments in src/main and the scenario/REQ comments in
src/test. Flag any requirement with no test, and any test not tied to a requirement.
Add a summary line: "X of Y requirements have full code+test coverage."
```

Review it manually — make sure the pass/fail column reflects the **actual** `mvn test` result.

✅ **Done when:** `mvn test` is green, matrix exists and is honest. Commit: *"Part 4: tests +
traceability."*

---

## PART 5 — Written Report (REPORT.md) (40 min)

**What:** Answer all 12 questions in `week2-url-shortener/REPORT.md`. Graded at 15%.
**Why:** This is where you show you *understood* SDD, not just executed steps. Be specific and
honest — graders reward real observations (including "the review missed X").

Create the file and answer each. Cheat-sheet of where each answer comes from:

| Q | What it asks | Where your evidence is |
|---|--------------|------------------------|
| Q1 | Did spec-first improve quality vs "build me a URL shortener"? | Compare your clean, validated output to what a one-liner prompt would give (vague, no validation/expiry/SSRF). |
| Q2 | Value of YAML templates vs ad-hoc? | Reusability, version field, consistent output, easy onboarding. |
| Q3 | Your self-critique loop — real issues found? missed? | `docs/self-critique-log.md`. Name a real catch (SSRF) and a miss (e.g., race condition on code collision). |
| Q4 | How complete was traceability? gaps? | `docs/traceability-matrix.md`. |
| Q5 | Role of Mermaid diagrams — reveal missed reqs? | Your Part 2 note (e.g., the state diagram → soft-delete flag). |
| Q6 | If PM adds "password protection" mid-sprint? | Explain: update spec (delta) → new REQ-ID → impact analysis on affected components → regenerate tests. |
| Q7 | Show your best YAML template, explain each field | Paste `code-reviewer.yaml`; explain name/version/role/task/output_schema/tags. |
| Q8 | The JSON schema + the validated output | `docs/schema-enforced-output.md`. |
| Q9 | Traceability matrix + how many fully covered | Paste the matrix + the summary count. |
| Q10 | % of auto-gen tests passing first run + failure types | The number you recorded in Step 4.2 — be honest. |
| Q11 | A Gherkin scenario + the test code from it | Paste one scenario from the spec and its matching test method. |
| Q12 | Time breakdown per part; longest & why | Your timer notes. Usually Part 3 is longest. |

> **Honesty pays:** Q3, Q4, Q10 explicitly reward admitting gaps and low first-run pass rates. A
> "100% perfect" report reads as fake. Report the real numbers and what you learned.

---

## 6. Final Submission

1. **Review the checklist** in Section 1 — every folder/file present.
2. **Run the tests one last time** so the README/matrix reflects a green build:
   `cd week2-url-shortener && ./mvnw test`
3. **Add a short `week2-url-shortener/README.md`**: what the project is, how to run it
   (`./mvnw spring-boot:run`), how to test (`./mvnw test`), and a link to `REPORT.md`.
4. **Commit and push** to GitHub. Suggested final commit: *"Week 2: spec-driven URL shortener — complete."*
5. **Submit the GitHub link** (and the repo already contains your report).

---

## 7. Common Pitfalls (save yourself pain)

- **Don't generate the whole app in one prompt.** Small chunks = reviewable, traceable code.
- **Don't fix tests before recording the first-run result** — you'll lose the Q10 metric.
- **Don't skip the `// REQ-SHORT-NNN` comments** — they're the whole "traceability" grade.
- **Don't hard-delete URLs** — use a status flag so the state diagram (active→expired→deleted) is
  real and testable.
- **SSRF/open-redirect is the headline security issue** for a URL shortener — make sure your
  validator blocks `file://`, `localhost`, and private IP ranges. Reviewers look for this.
- **Keep the spec the single source of truth** — if you change behavior in code, update the spec and
  regenerate/adjust the test. That's the discipline being graded.

---

*Generated as your working reference. Update the checkboxes/notes as you go.*
// Integration test suite generated from specs/url-shortener.yaml (test-generator.yaml).
// JUnit 5 + Spring Boot MockMvc against in-memory H2. Runs with `mvn test`.
//
// Coverage: at least one test per Gherkin scenario SCN-001..SCN-008, plus the required
// edge cases (invalid URLs, duplicates, expired URLs, missing fields, malicious/SSRF URLs,
// not-found short codes). Each test names the scenario ID and REQ-SHORT-NNN it verifies.
package com.business.urlshortener;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.business.urlshortener.entity.Url;
import com.business.urlshortener.repository.ClickEventRepository;
import com.business.urlshortener.repository.UrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @BeforeEach
    void resetDatabase() {
        clickEventRepository.deleteAll();
        urlRepository.deleteAll();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private String body(String url) {
        return "{\"url\":\"" + url + "\"}";
    }

    /** Shortens a URL via the API and returns the generated short code. */
    private String shortenAndGetCode(String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(url)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("short_code").asText();
    }

    // ---------------------------------------------------------------------------------------
    // SCN-001 — Shorten a valid URL (happy path)  [REQ-SHORT-001, REQ-SHORT-002]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn001_shortenValidUrl_returns201WithShortCode() throws Exception {
        // Scenario SCN-001 verifies REQ-SHORT-001 (create short code) and REQ-SHORT-002 (unique code).
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://www.example.com/some/very/long/path")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_code", notNullValue()))
                .andExpect(jsonPath("$.short_url", notNullValue()))
                .andExpect(jsonPath("$.original_url").value("https://www.example.com/some/very/long/path"));
    }

    // ---------------------------------------------------------------------------------------
    // SCN-002 — Redirect to the original URL (happy path)  [REQ-SHORT-003, REQ-SHORT-005]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn002_redirect_returns302ToOriginalUrl() throws Exception {
        // Scenario SCN-002 verifies REQ-SHORT-003 (302 redirect) and REQ-SHORT-005 (analytics tracked).
        String original = "https://www.example.com";
        String code = shortenAndGetCode(original);

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", original));

        // REQ-SHORT-005 — the click was recorded.
        Url stored = urlRepository.findByShortCode(code).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1L, stored.getClickCount());
    }

    // ---------------------------------------------------------------------------------------
    // SCN-003 — Reject an invalid URL (error case)  [REQ-SHORT-008, REQ-SHORT-011]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn003_invalidUrl_returns400() throws Exception {
        // Scenario SCN-003 verifies REQ-SHORT-008 (reject invalid URL) and REQ-SHORT-011 (structured error).
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-a-valid-url")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    // ---------------------------------------------------------------------------------------
    // SCN-004 — Unknown short code returns not found (error case)  [REQ-SHORT-004, REQ-SHORT-011]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn004_unknownShortCode_returns404() throws Exception {
        // Scenario SCN-004 verifies REQ-SHORT-004 (unknown code -> 404) and REQ-SHORT-011 (structured error).
        mockMvc.perform(get("/{code}", "zzz999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------------------------------------------------------------------------------------
    // SCN-005 — Expired URL is gone (edge case)  [REQ-SHORT-007]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn005_expiredUrl_returns410() throws Exception {
        // Scenario SCN-005 verifies REQ-SHORT-007 (expired short URL -> 410 Gone).
        Url expired = new Url("old123", "https://www.example.com",
                Instant.now().minus(1, ChronoUnit.DAYS));
        urlRepository.save(expired);

        mockMvc.perform(get("/{code}", "old123"))
                .andExpect(status().isGone());
    }

    // ---------------------------------------------------------------------------------------
    // SCN-006 — Duplicate URL returns existing short code (edge case)  [REQ-SHORT-009]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn006_duplicateUrl_returnsExistingCodeWith200() throws Exception {
        // Scenario SCN-006 verifies REQ-SHORT-009 (duplicate -> existing code, no new record).
        String url = "https://www.example.com/dup";
        String firstCode = shortenAndGetCode(url);

        MvcResult second = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(url)))
                .andExpect(status().isOk())
                .andReturn();
        String secondCode = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("short_code").asText();

        org.junit.jupiter.api.Assertions.assertEquals(firstCode, secondCode);
        org.junit.jupiter.api.Assertions.assertEquals(1, urlRepository.count());
    }

    // ---------------------------------------------------------------------------------------
    // SCN-007 — Reject a malicious/SSRF URL (edge case)  [REQ-SHORT-010, REQ-SHORT-012]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn007_ssrfUrl_returns400() throws Exception {
        // Scenario SCN-007 verifies REQ-SHORT-010 (block internal host) and REQ-SHORT-012 (no open redirect).
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("http://169.254.169.254/latest/meta-data")))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(0, urlRepository.count());
    }

    // ---------------------------------------------------------------------------------------
    // SCN-008 — Retrieve analytics for a short code (happy path)  [REQ-SHORT-005, REQ-SHORT-006]
    // ---------------------------------------------------------------------------------------
    @Test
    void scn008_analytics_returnsClickCountAndReferrers() throws Exception {
        // Scenario SCN-008 verifies REQ-SHORT-005 (click count / last accessed) and REQ-SHORT-006 (analytics endpoint).
        String code = shortenAndGetCode("https://www.example.com/analytics");

        // Three hits, one carrying a Referer header.
        mockMvc.perform(get("/{code}", code).header("Referer", "https://ref.example.com"))
                .andExpect(status().isFound());
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());

        mockMvc.perform(get("/api/v1/urls/{code}/analytics", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.short_code").value(code))
                .andExpect(jsonPath("$.click_count").value(3))
                .andExpect(jsonPath("$.last_accessed_at", notNullValue()))
                .andExpect(jsonPath("$.referrers", notNullValue()))
                .andExpect(jsonPath("$.referrers.length()", greaterThanOrEqualTo(3)));
    }

    // ---------------------------------------------------------------------------------------
    // Additional required edge cases
    // ---------------------------------------------------------------------------------------

    @Test
    void missingUrlField_returns400() throws Exception {
        // Edge case (missing fields) — REQ-SHORT-008 / REQ-SHORT-011: @NotBlank url -> 400.
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void nonHttpScheme_returns400() throws Exception {
        // Edge case (malicious/unsupported scheme) — REQ-SHORT-008 / REQ-SHORT-010.
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ftp://example.com/resource")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void localhostUrl_isBlocked_returns400() throws Exception {
        // Edge case (SSRF via localhost) — REQ-SHORT-010.
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("http://localhost:8080/admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyticsForUnknownCode_returns404() throws Exception {
        // Edge case (not-found short code on analytics) — REQ-SHORT-004 / REQ-SHORT-006.
        mockMvc.perform(get("/api/v1/urls/{code}/analytics", "missing1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
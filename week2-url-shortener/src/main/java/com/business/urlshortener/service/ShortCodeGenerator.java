// TASK-04 — Short-code generation
// Implements: REQ-SHORT-002 (unique, URL-safe alphanumeric short codes with collision retry).
package com.business.urlshortener.service;

import com.business.urlshortener.repository.UrlRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    // URL-safe alphanumeric alphabet (base62).
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final UrlRepository urlRepository;

    public ShortCodeGenerator(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    /**
     * Generates a short code that is unique across stored URLs, retrying on collision.
     * REQ-SHORT-002.
     */
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!urlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique short code after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
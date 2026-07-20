// Smoke test — verifies the Spring context loads against in-memory H2.
package com.business.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UrlShortenerApplicationTests {

    @Test
    void contextLoads() {
        // Fails if any bean wiring or JPA mapping is broken.
    }
}

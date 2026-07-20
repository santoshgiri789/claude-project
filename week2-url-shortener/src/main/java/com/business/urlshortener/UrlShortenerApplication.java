// TASK-01 — Project scaffolding & configuration
// Implements foundation for all REQ-SHORT-* (application bootstrap).
package com.business.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
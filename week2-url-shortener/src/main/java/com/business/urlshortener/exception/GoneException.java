// Service layer — domain exception
// Implements: REQ-SHORT-007 (expired short URL -> HTTP 410 Gone).
package com.business.urlshortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class GoneException extends RuntimeException {
    public GoneException(String message) {
        super(message);
    }
}
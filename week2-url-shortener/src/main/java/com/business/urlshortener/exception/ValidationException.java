// Service layer — domain exception
// Implements: REQ-SHORT-008, REQ-SHORT-010 (invalid / malicious URL -> HTTP 400).
package com.business.urlshortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
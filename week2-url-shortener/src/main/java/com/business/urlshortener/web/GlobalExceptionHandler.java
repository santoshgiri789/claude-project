// TASK-08 — Structured error handling
// Implements: REQ-SHORT-004, REQ-SHORT-007, REQ-SHORT-008, REQ-SHORT-010,
//             REQ-SHORT-011 (structured JSON errors, correct status codes, no info leakage).
package com.business.urlshortener.web;

import com.business.urlshortener.dto.ErrorResponse;
import com.business.urlshortener.exception.GoneException;
import com.business.urlshortener.exception.NotFoundException;
import com.business.urlshortener.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // REQ-SHORT-008, REQ-SHORT-010 — invalid or malicious URL -> 400.
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // REQ-SHORT-008 — Bean Validation failures on the request body -> 400.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Request validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    // REQ-SHORT-004 — unknown short code -> 404.
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // REQ-SHORT-007 — expired short URL -> 410 Gone.
    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ErrorResponse> handleGone(GoneException ex) {
        return build(HttpStatus.GONE, ex.getMessage());
    }

    // REQ-SHORT-011 — catch-all: never leak stack traces or internal details.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}
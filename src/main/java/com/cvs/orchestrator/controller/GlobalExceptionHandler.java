package com.cvs.orchestrator.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Translates service-layer exceptions into meaningful HTTP responses.
 * Without this, Spring returns 500 for any unhandled RuntimeException.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 — resource not found. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Bad request";
        boolean isNotFound = msg.toLowerCase().contains("not found");
        HttpStatus status = isNotFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        log.warn("{}: {}", status.value(), msg);
        return ResponseEntity.status(status).body(error(status.value(), msg));
    }

    /**
     * 422 — resource exists but is in an invalid state
     * (e.g. secret found but cannot be decrypted — wrong encryption key).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleUnprocessable(IllegalStateException ex) {
        log.error("Unprocessable entity: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(error(422, ex.getMessage()));
    }

    /** 500 — catch-all for unexpected errors (still logged fully). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(500, "An unexpected error occurred: " + ex.getMessage()));
    }

    private Map<String, Object> error(int status, String message) {
        return Map.of(
                "status", status,
                "error", message,
                "timestamp", Instant.now().toString());
    }
}

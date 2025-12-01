package com.secufusion.iam.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ===============================
    // HANDLE CUSTOM BUSINESS EXCEPTION
    // ===============================
    @ExceptionHandler(KeycloakOperationException.class)
    public ResponseEntity<Map<String, Object>> handleKeycloakException(KeycloakOperationException ex) {
        log.error("[{} - {}]: {}", ex.getErrorCode(), ex.getErrorNumber(), ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorCode", ex.getErrorCode());
        body.put("errorNumber", ex.getErrorNumber());
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // ===============================
    // HANDLE NOT FOUND EXCEPTION
    // ===============================
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource missing: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorCode", "RESOURCE_NOT_FOUND");
        body.put("errorNumber", 4040);
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    // ===============================
    // FALLBACK FOR UNKNOWN ERRORS
    // ===============================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected internal error: {}", ex.getMessage(), ex);

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorCode", "INTERNAL_SERVER_ERROR");
        body.put("errorNumber", 5000);
        body.put("message", "An unexpected error occurred. Please try again later.");
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

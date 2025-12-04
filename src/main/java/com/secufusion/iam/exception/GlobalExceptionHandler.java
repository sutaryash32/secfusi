package com.secufusion.iam.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ===============================
    // Handle KeycloakOperationException
    // ===============================
    @ExceptionHandler(KeycloakOperationException.class)
    public ResponseEntity<Map<String, Object>> handleKeycloakException(KeycloakOperationException ex) {
        log.error("[{} - {}]: {}", ex.getErrorCode(), ex.getErrorNumber(), ex.getMessage());

        return buildResponse(
                ex.getErrorCode(),
                ex.getErrorNumber(),
                ex.getMessage(),
                HttpStatus.BAD_REQUEST
        );
    }

    // ===============================
    // Handle Resource Not Found
    // ===============================
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource missing: {}", ex.getMessage());

        return buildResponse(
                "RESOURCE_NOT_FOUND",
                4040,
                ex.getMessage(),
                HttpStatus.NOT_FOUND
        );
    }

    // ===============================
    // Handle Access Denied
    // (Used when parent tenant tries access)
    // ===============================
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        return buildResponse(
                "ACCESS_DENIED",
                4030,
                ex.getMessage(),
                HttpStatus.FORBIDDEN
        );
    }

    // ===============================
    // Handle Validation Failures
    // (invalid payload, missing fields)
    // ===============================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");

        log.warn("Validation error: {}", errorMessage);

        return buildResponse(
                "VALIDATION_ERROR",
                4001,
                errorMessage,
                HttpStatus.BAD_REQUEST
        );
    }

    // ===============================
    // Handle ALL Remaining Unexpected Errors
    // ===============================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected internal error: {}", ex.getMessage(), ex);

        return buildResponse(
                "INTERNAL_SERVER_ERROR",
                5000,
                "An unexpected error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // ===============================
    // Helper to Build Standard Response Format
    // ===============================
    private ResponseEntity<Map<String, Object>> buildResponse(
            String errorCode,
            int errorNumber,
            String message,
            HttpStatus status
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorCode", errorCode);
        body.put("errorNumber", errorNumber);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, status);
    }
}

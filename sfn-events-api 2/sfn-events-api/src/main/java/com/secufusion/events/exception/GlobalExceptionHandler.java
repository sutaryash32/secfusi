package com.secufusion.events.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

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
    // Handle Resource Conflict
    // ===============================
    @ExceptionHandler(EventException.class)
    public ResponseEntity<Map<String, Object>> handleEventException(EventException ex) {
        log.warn("Event error: {}", ex.getMessage());

        return buildResponse(
                "EVENT_ERROR",
                4002,
                ex.getMessage(),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleResourceConflict(ResourceConflictException ex) {
        log.warn("Resource conflict: {}", ex.getMessage());
        return buildResponse(
                "RESOURCE_CONFLICT",
                4090,
                ex.getMessage(),
                HttpStatus.CONFLICT
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
    // Handle ResponseStatusException — preserves the intended HTTP status code
    // (Without this, handleGenericException swallows it and returns 500)
    // ===============================
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        int statusCode = ex.getStatusCode().value();
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        log.warn("Response status exception: status={}, reason={}", statusCode, reason);

        return buildResponse(
                "REQUEST_ERROR",
                statusCode,
                reason,
                HttpStatus.valueOf(statusCode)
        );
    }

    // ===============================
    // Handle ALL Remaining Unexpected Errors
    // ===============================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected internal error: {}", ex.getMessage(), ex);

        // Include root cause in message to help diagnose without log access
        String rootCause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        String debugMessage = ex.getClass().getSimpleName() + ": " + rootCause;

        return buildResponse(
                "INTERNAL_SERVER_ERROR",
                5000,
                debugMessage,
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler({
            InvalidTokenException.class,
            TokenExpiredException.class,
            TokenMismatchException.class,
            TokenValidationException.class,
            MissingAuthorizationException.class
    })
    public ResponseEntity<Map<String, Object>> handleAuthExceptions(RuntimeException ex) {

        log.warn("Authentication error: {}", ex.getMessage());

        return buildResponse(
                "AUTHENTICATION_FAILED",
                4010,
                ex.getMessage(),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {

        log.warn("Spring access denied: {}", ex.getMessage());

        return buildResponse(
                "ACCESS_DENIED",
                4030,
                ex.getMessage(),
                HttpStatus.FORBIDDEN
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

package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle all custom GlobalException and its subclasses
     */
    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<Map<String, Object>> handleGlobal(GlobalException ex) {
        log.error("GlobalException caught: {}", ex.getMessage(), ex);

        String errorCode = (ex.getErrorCode() != null && !ex.getErrorCode().isBlank())
                ? ex.getErrorCode()
                : defaultCode(ex);

        HttpStatus status = mapStatus(errorCode);

        return build(errorCode, ex.getMessage(), status);
    }

    /**
     * Handle Bean Validation errors (@Valid annotation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return build(
                ResponseCodes.VALIDATION_ERROR,
                message.isBlank() ? "Validation failed" : message,
                HttpStatus.BAD_REQUEST
        );
    }

    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParams(MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getParameterName());

        return build(
                ResponseCodes.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                HttpStatus.BAD_REQUEST
        );
    }

    /**
     * Handle type mismatch errors
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter: {}", ex.getName());

        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        return build(
                ResponseCodes.BAD_REQUEST,
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    /**
     * Handle malformed JSON errors
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());

        return build(
                ResponseCodes.BAD_REQUEST,
                "Malformed JSON request",
                HttpStatus.BAD_REQUEST
        );
    }

    /**
     * Handle database constraint violations (unique, foreign key, etc.)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Database constraint violation: {}", ex.getMessage(), ex);

        String message = "Database constraint violation";
        String errorCode = ResponseCodes.RESOURCE_CONFLICT;

        // Try to extract meaningful message from exception
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("unique constraint") || ex.getMessage().contains("Duplicate entry")) {
                message = "Resource already exists with the same unique value";
            } else if (ex.getMessage().contains("foreign key constraint")) {
                message = "Cannot delete or update resource due to existing references";
            } else if (ex.getMessage().contains("not-null constraint")) {
                message = "Required field cannot be null";
            }
        }

        return build(errorCode, message, HttpStatus.CONFLICT);
    }

    /**
     * Handle general database access errors
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
        log.error("Database access error: {}", ex.getMessage(), ex);

        return build(
                ResponseCodes.INTERNAL_SERVER_ERROR,
                "Database operation failed",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        return build(
                ResponseCodes.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Invalid argument provided",
                HttpStatus.BAD_REQUEST
        );
    }

    /**
     * Handle IllegalStateException
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage(), ex);

        return build(
                ResponseCodes.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Operation failed due to invalid state",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    /**
     * Handle NullPointerException
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException ex) {
        log.error("Null pointer exception: {}", ex.getMessage(), ex);

        return build(
                ResponseCodes.INTERNAL_SERVER_ERROR,
                "Internal server error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    /**
     * Catch-all for any other unhandled exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return build(
                ResponseCodes.INTERNAL_SERVER_ERROR,
                "Unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // ---------------------------
    // Helper Methods
    // ---------------------------

    private String defaultCode(GlobalException ex) {
        if (ex instanceof ResourceNotFoundException) {
            return ResponseCodes.RESOURCE_NOT_FOUND;
        } else if (ex instanceof ResourceConflictException) {
            return ResponseCodes.RESOURCE_CONFLICT;
        } else if (ex instanceof ValidationException) {
            return ResponseCodes.VALIDATION_ERROR;
        } else if (ex instanceof AccessDeniedException) {
            return ResponseCodes.ACCESS_DENIED;
        } else if (ex instanceof InvalidTokenException) {
            return ResponseCodes.INVALID_TOKEN;
        } else if (ex instanceof KeycloakOperationException) {
            return ResponseCodes.KEYCLOAK_OPERATION_FAILED;
        } else if (ex instanceof ExternalServiceException) {
            return ResponseCodes.INTERNAL_SERVER_ERROR;
        } else if (ex instanceof DatabaseException) {
            return ResponseCodes.INTERNAL_SERVER_ERROR;
        }
        return ResponseCodes.BAD_REQUEST;
    }

    private HttpStatus mapStatus(String code) {
        return switch (code) {
            // Not Found
            case ResponseCodes.RESOURCE_NOT_FOUND,
                 ResponseCodes.TENANT_NOT_FOUND,
                 ResponseCodes.AUTH_PROVIDER_CONFIG_MISSING -> HttpStatus.NOT_FOUND;

            // Conflict
            case ResponseCodes.RESOURCE_CONFLICT,
                 ResponseCodes.ORGANIZATION_NAME_ALREADY_EXISTS,
                 ResponseCodes.DOMAIN_ALREADY_EXISTS,
                 ResponseCodes.ORGANIZATION_PHONE_ALREADY_EXISTS,
                 ResponseCodes.ORGANIZATION_EMAIL_ALREADY_EXISTS,
                 ResponseCodes.ADMIN_EMAIL_ALREADY_EXISTS,
                 ResponseCodes.ADMIN_PHONE_ALREADY_EXISTS,
                 ResponseCodes.ORGANIZATION_REALM_ALREADY_EXISTS,
                 ResponseCodes.TENANT_ALREADY_ACTIVE -> HttpStatus.CONFLICT;

            // Forbidden
            case ResponseCodes.ACCESS_DENIED -> HttpStatus.FORBIDDEN;

            // Unauthorized
            case ResponseCodes.INVALID_TOKEN,
                 ResponseCodes.TOKEN_EXPIRED,
                 ResponseCodes.TOKEN_MISMATCH,
                 ResponseCodes.TOKEN_VALIDATION_FAILED,
                 ResponseCodes.MISSING_AUTHORIZATION,
                 ResponseCodes.AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;

            // Internal Server Error
            case ResponseCodes.INTERNAL_SERVER_ERROR,
                 ResponseCodes.KEYCLOAK_OPERATION_FAILED,
                 ResponseCodes.REALM_CREATION_FAILED,
                 ResponseCodes.CLIENT_CREATION_FAILED,
                 ResponseCodes.EXTENSION_CLIENT_CREATION_FAILED,
                 ResponseCodes.USER_CREATION_FAILED,
                 ResponseCodes.USER_CONFIGURATION_FAILED,
                 ResponseCodes.UNKNOWN_PROVISIONING_STATE,
                 ResponseCodes.EMAIL_SEND_FAILED,
                 ResponseCodes.USERNAME_GENERATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;

            // Bad Request (default and validation errors)
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private ResponseEntity<Map<String, Object>> build(
            String errorCode,
            String message,
            HttpStatus status
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorCode", errorCode);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return new ResponseEntity<>(body, status);
    }
}

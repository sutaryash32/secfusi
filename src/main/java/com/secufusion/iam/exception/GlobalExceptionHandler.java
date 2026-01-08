package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<Map<String, Object>> handleGlobal(GlobalException ex) {

        String errorCode = (ex.getErrorCode() != null && !ex.getErrorCode().isBlank())
                ? ex.getErrorCode()
                : defaultCode(ex);

        HttpStatus status = mapStatus(errorCode);

        return build(errorCode, ex.getMessage(), status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {

        return build(
                ResponseCodes.INTERNAL_SERVER_ERROR,
                "Unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // ---------------------------
    private String defaultCode(GlobalException ex) {
        if (ex instanceof ResourceNotFoundException) {
            return ResponseCodes.RESOURCE_NOT_FOUND;
        }
        return ResponseCodes.BAD_REQUEST;
    }

    private HttpStatus mapStatus(String code) {
        return switch (code) {
            case ResponseCodes.RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ResponseCodes.RESOURCE_CONFLICT -> HttpStatus.CONFLICT;
            case ResponseCodes.ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case ResponseCodes.INVALID_TOKEN,
                 ResponseCodes.TOKEN_EXPIRED,
                 ResponseCodes.TOKEN_MISMATCH,
                 ResponseCodes.TOKEN_VALIDATION_FAILED,
                 ResponseCodes.MISSING_AUTHORIZATION,
                 ResponseCodes.AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
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

package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

/**
 * Exception thrown when external service calls fail (Azure AD, Keycloak, SMTP, etc.)
 */
public class ExternalServiceException extends GlobalException {

    public ExternalServiceException(String message) {
        super(message, ResponseCodes.INTERNAL_SERVER_ERROR);
    }

    public ExternalServiceException(String message, String errorCode) {
        super(message, errorCode);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, ResponseCodes.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }

    public ExternalServiceException(String message, String errorCode, Throwable cause) {
        super(message, errorCode);
        initCause(cause);
    }
}

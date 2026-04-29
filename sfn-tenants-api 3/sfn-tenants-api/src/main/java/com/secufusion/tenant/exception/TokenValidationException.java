package com.secufusion.tenant.exception;

import com.secufusion.tenant.util.ResponseCodes;

public class TokenValidationException extends GlobalException {

    public TokenValidationException(String message) {
        super(message, ResponseCodes.TOKEN_VALIDATION_FAILED);
    }

    public TokenValidationException(String message, String errorCode) {
        super(message, errorCode);
    }
}
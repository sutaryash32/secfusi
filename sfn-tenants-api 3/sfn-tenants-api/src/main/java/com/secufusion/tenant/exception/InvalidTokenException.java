package com.secufusion.tenant.exception;

import com.secufusion.tenant.util.ResponseCodes;

public class InvalidTokenException extends GlobalException {

    public InvalidTokenException(String message) {
        super(message, ResponseCodes.INVALID_TOKEN);
    }

    public InvalidTokenException(String message, String errorCode) {
        super(message, errorCode);
    }
}

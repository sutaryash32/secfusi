package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class InvalidTokenException extends GlobalException {

    public InvalidTokenException(String message) {
        super(message, ResponseCodes.INVALID_TOKEN);
    }

    public InvalidTokenException(String message, String errorCode) {
        super(message, errorCode);
    }
}

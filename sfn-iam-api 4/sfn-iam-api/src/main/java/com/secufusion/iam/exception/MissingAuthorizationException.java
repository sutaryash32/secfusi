package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class MissingAuthorizationException extends GlobalException {

    public MissingAuthorizationException(String message) {
        super(message, ResponseCodes.MISSING_AUTHORIZATION);
    }

    public MissingAuthorizationException(String message, String errorCode) {
        super(message, errorCode);
    }
}

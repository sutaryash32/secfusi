package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class AccessDeniedException extends GlobalException {

    public AccessDeniedException(String message) {
        super(message, ResponseCodes.ACCESS_DENIED);
    }

    public AccessDeniedException(String message, String errorCode) {
        super(message, errorCode);
    }
}

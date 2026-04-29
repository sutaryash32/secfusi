package com.secufusion.tenant.exception;

import com.secufusion.tenant.util.ResponseCodes;

public class AccessDeniedException extends GlobalException {

    public AccessDeniedException(String message) {
        super(message, ResponseCodes.ACCESS_DENIED);
    }

    public AccessDeniedException(String message, String errorCode) {
        super(message, errorCode);
    }
}

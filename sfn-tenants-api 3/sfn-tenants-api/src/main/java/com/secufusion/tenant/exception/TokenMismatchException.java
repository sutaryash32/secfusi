package com.secufusion.tenant.exception;

import com.secufusion.tenant.util.ResponseCodes;

public class TokenMismatchException extends GlobalException {

    public TokenMismatchException(String message) {
        super(message, ResponseCodes.TOKEN_MISMATCH);
    }

    public TokenMismatchException(String message, String errorCode) {
        super(message, errorCode);
    }
}

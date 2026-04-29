package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class TokenMismatchException extends GlobalException {

    public TokenMismatchException(String message) {
        super(message, ResponseCodes.TOKEN_MISMATCH);
    }

    public TokenMismatchException(String message, String errorCode) {
        super(message, errorCode);
    }
}

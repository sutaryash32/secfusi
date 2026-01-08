package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class TokenExpiredException extends GlobalException {

    public TokenExpiredException(String message) {
        super(message, ResponseCodes.TOKEN_EXPIRED);
    }

    public TokenExpiredException(String message, String errorCode) {
        super(message, errorCode);
    }
}

package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class BadRequestException extends GlobalException {

    public BadRequestException(String message) {
        super(message, ResponseCodes.BAD_REQUEST);
    }

    public BadRequestException(String message, String errorCode) {
        super(message, errorCode);
    }
}

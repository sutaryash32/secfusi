package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class ValidationException extends GlobalException {

    public ValidationException(String message) {
        super(message, ResponseCodes.VALIDATION_ERROR);
    }

    public ValidationException(String message, String errorCode) {
        super(message, errorCode);
    }
}

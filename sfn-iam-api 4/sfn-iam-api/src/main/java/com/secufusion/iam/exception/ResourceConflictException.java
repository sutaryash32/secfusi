package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class ResourceConflictException extends GlobalException {

    public ResourceConflictException(String message) {
        super(message, ResponseCodes.RESOURCE_CONFLICT);
    }

    public ResourceConflictException(String message, String errorCode) {
        super(message, errorCode);
    }
}

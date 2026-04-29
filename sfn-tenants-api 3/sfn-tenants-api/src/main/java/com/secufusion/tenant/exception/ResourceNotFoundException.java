package com.secufusion.tenant.exception;

import com.secufusion.tenant.util.ResponseCodes;

public class ResourceNotFoundException extends GlobalException {

    public ResourceNotFoundException(String message) {
        super(message, ResponseCodes.RESOURCE_NOT_FOUND);
    }

    public ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }
}

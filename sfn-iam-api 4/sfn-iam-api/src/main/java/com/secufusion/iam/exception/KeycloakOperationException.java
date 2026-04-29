package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;
import lombok.Getter;

@Getter
public class KeycloakOperationException extends GlobalException {

    private final int errorNumber;

    public KeycloakOperationException(String message, int errorNumber) {
        super(message, ResponseCodes.AUTHENTICATION_FAILED);
        this.errorNumber = errorNumber;
    }

    public KeycloakOperationException(String message, int errorNumber, String errorCode) {
        super(message, errorCode);
        this.errorNumber = errorNumber;
    }
}

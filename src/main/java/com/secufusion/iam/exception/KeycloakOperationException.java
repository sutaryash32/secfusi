package com.secufusion.iam.exception;

import lombok.Getter;

@Getter
public class KeycloakOperationException extends RuntimeException {

    private final String errorCode;
    private final int errorNumber;

    public KeycloakOperationException(String errorCode, int errorNumber, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorNumber = errorNumber;
    }

    public KeycloakOperationException(String errorCode, int errorNumber, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorNumber = errorNumber;
    }

}

package com.secufusion.tenant.exception;

import lombok.Getter;

@Getter
public class GlobalException extends RuntimeException{

    private String errorCode;

    public GlobalException(String message) {
        super(message);
        this.errorCode = null;
    }

    // Explicit errorCode
    public GlobalException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

}

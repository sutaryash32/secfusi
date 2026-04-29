package com.secufusion.tenant.exception;


import com.secufusion.tenant.util.ResponseCodes;

public class InvalidDomainException extends GlobalException{
    public InvalidDomainException(String message) {
        super(message, ResponseCodes.INVALID_DOMAIN);
    }
    public InvalidDomainException(String message, String errorCode) {
        super(message, errorCode);
    }

}

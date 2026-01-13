package com.secufusion.iam.exception;

import com.secufusion.iam.util.ResponseCodes;

public class DatabaseException extends GlobalException {

    public DatabaseException(String message) {
        super(message, ResponseCodes.INTERNAL_SERVER_ERROR);
    }

    public DatabaseException(String message, String errorCode) {
        super(message, errorCode);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, ResponseCodes.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}

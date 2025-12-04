package com.secufusion.iam.exception;

public class EventException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EventException(String message) {
        super(message);
    }

    public EventException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventException(Throwable cause) {
        super(cause);
    }
}


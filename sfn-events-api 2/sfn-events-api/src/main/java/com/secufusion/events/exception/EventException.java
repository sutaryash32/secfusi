package com.secufusion.events.exception;

public class EventException extends RuntimeException{
    private static final long serialVersionUID = 1L;

    public EventException(String message) {
        super(message);
    }

    public EventException(String message, Throwable cause) {
        super(message, cause);
    }
}

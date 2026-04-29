package com.secufusion.events.exception;

public class TokenMismatchException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public TokenMismatchException(String message) { super(message); }
    public TokenMismatchException(String message, Throwable cause) { super(message, cause); }
}
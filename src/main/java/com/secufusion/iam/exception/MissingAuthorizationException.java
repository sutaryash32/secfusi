package com.secufusion.iam.exception;

public class MissingAuthorizationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public MissingAuthorizationException(String message) { super(message); }
    public MissingAuthorizationException(String message, Throwable cause) { super(message, cause); }
}


package com.secufusion.iam.exception;

public class ResourceConflictException extends RuntimeException{
    public ResourceConflictException(String message) {
        super(message);
    }
}

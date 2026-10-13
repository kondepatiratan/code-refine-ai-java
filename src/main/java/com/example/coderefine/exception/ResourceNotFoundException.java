package com.example.coderefine.exception;

/** Thrown when a requested resource (e.g. a session) does not exist for the current user. Mapped to 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

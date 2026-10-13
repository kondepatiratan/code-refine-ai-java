package com.example.coderefine.exception;

/** Thrown when login fails (unknown email or wrong password). Mapped to 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}

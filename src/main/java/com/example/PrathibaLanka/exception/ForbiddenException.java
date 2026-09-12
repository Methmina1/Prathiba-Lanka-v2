package com.example.PrathibaLanka.exception;

/** Thrown when an authenticated user tries to act on a resource they do not own. Maps to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}

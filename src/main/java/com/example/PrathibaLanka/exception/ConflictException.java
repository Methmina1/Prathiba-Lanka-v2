package com.example.PrathibaLanka.exception;

/** The request is valid but clashes with data that already exists (mapped to 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

package com.example.PrathibaLanka.exception;

/** The uploaded file is bigger than the configured limit (mapped to 413). */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}

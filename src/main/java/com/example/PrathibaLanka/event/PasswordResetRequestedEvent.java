package com.example.PrathibaLanka.event;

/**
 * Raised when an admin asks for a password-reset code, and handled on the mail pool.
 *
 * <p>Published rather than mailed directly for the usual reason: the request should not wait on an
 * SMTP conversation, and - more to the point here - the endpoint has to answer in the same time and
 * with the same words whether or not the address belongs to an account. Sending inline would make
 * the two cases measurably different.
 *
 * <p>The code travels in the event because it exists in plaintext only inside this request: the
 * database holds its hash.
 */
public record PasswordResetRequestedEvent(
        Long adminId,
        String email,
        String fullName,
        String code,
        int validForMinutes
) {
}

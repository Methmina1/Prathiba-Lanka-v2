package com.example.PrathibaLanka.util;

import com.example.PrathibaLanka.exception.BadRequestException;

import java.nio.charset.StandardCharsets;

/**
 * What counts as an acceptable new password.
 *
 * <p>Length is the requirement that actually helps, so length is what is asked for - long enough to
 * be worth hashing, short enough to stay inside what BCrypt reads. BCrypt only looks at the first 72
 * bytes of its input, so a longer "password" would have its tail silently ignored: two different
 * passwords that share their first 72 bytes would be interchangeable, which is not a thing to leave
 * unsaid to the person typing it.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** @throws BadRequestException with a message meant to be shown to the person typing it */
    public static void require(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Please enter a new password.");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BadRequestException(
                    "A password needs at least " + MIN_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            // Counted in bytes rather than characters: an accented character or a Sinhala letter can
            // be several bytes each, and it is bytes that BCrypt stops reading.
            throw new BadRequestException(
                    "That password is too long - keep it under " + MAX_BYTES + " bytes.");
        }
    }
}

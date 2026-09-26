package com.example.PrathibaLanka.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One emailed password-reset code for one admin.
 *
 * <p>The code itself is never stored: {@link #codeHash} is a BCrypt hash of it, so the table cannot
 * be read for a working code and the comparison is deliberately slow enough to make guessing through
 * the API expensive. {@link #attempts} bounds that further - a code dies after a handful of wrong
 * tries rather than staying guessable for its whole lifetime.
 *
 * <p>The admin is held as an id rather than an association because nothing ever needs to walk from a
 * code to an admin: the lookup always goes the other way.
 */
@Entity
@Table(name = "password_reset_code")
@Data
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reset_id")
    private Long resetId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** Set the moment the code is used, so a second attempt with the same code cannot succeed. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(nullable = false)
    private int attempts;
}

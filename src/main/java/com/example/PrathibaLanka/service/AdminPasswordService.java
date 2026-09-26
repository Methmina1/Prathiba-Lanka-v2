package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.PasswordResetCode;
import com.example.PrathibaLanka.event.PasswordResetRequestedEvent;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.PasswordResetCodeRepository;
import com.example.PrathibaLanka.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Changing an admin's password from inside the application.
 *
 * <p>Two ways in, because they answer different situations. An admin who knows their password changes
 * it directly, which costs nothing and needs no mail. An admin who has forgotten it asks for a code
 * by email - the account that can read that inbox is the account that gets back in, which is the same
 * proof of ownership the booking PIN relies on.
 *
 * <p>Both paths write {@code password_changed_at}, so sessions that were already open stop working:
 * see {@code JwtAuthenticationFilter}.
 */
@Service
@RequiredArgsConstructor
public class AdminPasswordService {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordService.class);

    /** How long an emailed code is good for: long enough to find the mail, short enough to matter. */
    public static final int CODE_VALID_MINUTES = 10;

    /** Wrong guesses allowed against one code before it is finished. */
    public static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRepository adminRepository;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    /** What a reset attempt did. Returned rather than thrown, for the reason given in resetPassword. */
    public enum Outcome { CHANGED, INVALID_CODE }

    /** The everyday path: the admin is signed in and knows the password they are replacing. */
    @Transactional
    public void changePassword(Long adminId, String currentPassword, String newPassword) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminId));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new BadRequestException("That is not your current password.");
        }

        PasswordPolicy.require(newPassword);

        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            throw new BadRequestException("The new password is the same as the current one.");
        }

        apply(admin, newPassword);
        log.info("Admin {} changed their own password from the console.", admin.getEmail());
    }

    /**
     * Asks for a reset code by email.
     *
     * <p>Answers the same way whatever happens, and the controller says so in the same words: whether
     * an address has an admin account behind it is not something this endpoint is willing to tell
     * anybody who can type an address into it. The mail is published as an event so that it leaves on
     * the mail pool - which also removes the pause that would otherwise make a real account slower to
     * answer than an imaginary one.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = normalize(email);

        Admin admin = adminRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (admin == null) {
            log.info("Password reset asked for {}, which has no admin account. Nothing sent.", normalized);
            return;
        }

        // One live code at a time: asking twice must not leave the first one working.
        resetCodeRepository.discardOutstanding(admin.getAdminId());

        String code = code();
        PasswordResetCode row = new PasswordResetCode();
        row.setAdminId(admin.getAdminId());
        row.setCodeHash(passwordEncoder.encode(code));
        row.setRequestedAt(LocalDateTime.now());
        row.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_VALID_MINUTES));
        resetCodeRepository.save(row);

        events.publishEvent(new PasswordResetRequestedEvent(
                admin.getAdminId(), admin.getEmail(), admin.getFullName(), code, CODE_VALID_MINUTES));
    }

    /**
     * Spends a code and sets the new password.
     *
     * <p>Returns an outcome instead of throwing {@code BadRequestException}, and that is not a style
     * preference: a wrong code has to *increment the attempt counter*, and an exception thrown here
     * would roll the transaction back, discard the increment, and leave a code that can be guessed at
     * for its entire lifetime. The controller turns {@link Outcome#INVALID_CODE} into the 400.
     *
     * <p>A code that does not exist, one that has expired, one belonging to an address with no
     * account, and one that has been got wrong too many times all produce the same answer.
     */
    @Transactional
    public Outcome resetPassword(String email, String code, String newPassword) {
        PasswordPolicy.require(newPassword);

        Admin admin = adminRepository.findByEmailIgnoreCase(normalize(email)).orElse(null);
        if (admin == null) {
            return Outcome.INVALID_CODE;
        }

        PasswordResetCode row = resetCodeRepository
                .findFirstByAdminIdAndUsedAtIsNullOrderByRequestedAtDesc(admin.getAdminId())
                .orElse(null);

        if (row == null || row.getExpiresAt().isBefore(LocalDateTime.now())
                || row.getAttempts() >= MAX_ATTEMPTS) {
            return Outcome.INVALID_CODE;
        }

        if (code == null || !passwordEncoder.matches(code.trim(), row.getCodeHash())) {
            row.setAttempts(row.getAttempts() + 1);
            resetCodeRepository.save(row);
            log.info("Wrong password reset code for {} (attempt {} of {}).",
                    admin.getEmail(), row.getAttempts(), MAX_ATTEMPTS);
            return Outcome.INVALID_CODE;
        }

        apply(admin, newPassword);
        row.setUsedAt(LocalDateTime.now());
        resetCodeRepository.save(row);

        log.info("Admin {} reset their password using an emailed code.", admin.getEmail());
        return Outcome.CHANGED;
    }

    /**
     * Writes the new hash and the moment it changed.
     *
     * <p>The timestamp is the part that matters beyond this method: it is what makes an open session
     * stop working. Any code still outstanding belonged to the old password and goes with it.
     */
    private void apply(Admin admin, String newPassword) {
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admin.setPasswordChangedAt(LocalDateTime.now());
        adminRepository.save(admin);
        resetCodeRepository.discardOutstanding(admin.getAdminId());
    }

    /** Six digits from SecureRandom: uniform, and derived from nothing about the account. */
    private static String code() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

package com.example.PrathibaLanka.config;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.enums.Role;
import com.example.PrathibaLanka.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

/**
 * Guarantees a usable admin account, because self-registration only creates customers and there is
 * no API to create an admin.
 *
 * <p>The password comes from {@code BOOTSTRAP_ADMIN_PASSWORD} and has no default, so no password is
 * ever committed. What happens at startup:
 *
 * <ul>
 *   <li>no account and a password set - it is created with that password;</li>
 *   <li>no account and no password - nothing is created, and the log says how to make one;</li>
 *   <li>an account whose hash is not a valid BCrypt hash - the password is reset to the configured
 *       one, because such a hash could never match anything;</li>
 *   <li>{@code BOOTSTRAP_ADMIN_RESET_PASSWORD=true} - the password is reset to the configured one
 *       even though the stored hash is fine. This is the supported way to change an admin password:
 *       set the password and the flag, start once, then unset the flag.</li>
 * </ul>
 *
 * <p>Disable with {@code app.bootstrap-admin.enabled=false} in production.
 */
@Configuration
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    /** BCrypt hashes look like $2a$10$<53 chars>; anything else can never match a password. */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email}")
    private String adminEmail;

    @Value("${app.bootstrap-admin.password}")
    private String adminPassword;

    @Value("${app.bootstrap-admin.full-name}")
    private String adminFullName;

    @Value("${app.bootstrap-admin.reset-password:false}")
    private boolean resetPassword;

    @Bean
    @Order(1)   // the content seeding that follows attributes its rows to this account
    @ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner bootstrapAdminRunner() {
        return args -> {
            String email = adminEmail.trim().toLowerCase();
            String password = adminPassword == null ? "" : adminPassword.trim();
            Admin admin = adminRepository.findByEmailIgnoreCase(email).orElse(null);

            if (admin == null) {
                if (password.isEmpty()) {
                    log.error("No admin account for '{}' and BOOTSTRAP_ADMIN_PASSWORD is not set, so "
                            + "none was created. Set it (and BOOTSTRAP_ADMIN_EMAIL) and start again, or "
                            + "create the account yourself and disable app.bootstrap-admin.", email);
                    return;
                }
                Admin created = new Admin();
                created.setFullName(adminFullName);
                created.setEmail(email);
                created.setPasswordHash(passwordEncoder.encode(password));
                created.setRole(Role.ADMIN);
                adminRepository.save(created);

                log.warn("Created the admin account '{}' from BOOTSTRAP_ADMIN_PASSWORD. Change the "
                        + "password, or disable app.bootstrap-admin and provision admins yourself.", email);
                return;
            }

            if (resetPassword) {
                if (password.isEmpty()) {
                    log.error("BOOTSTRAP_ADMIN_RESET_PASSWORD is true but BOOTSTRAP_ADMIN_PASSWORD is "
                            + "empty - '{}' was left as it was.", email);
                    return;
                }
                admin.setPasswordHash(passwordEncoder.encode(password));
                if (!email.equalsIgnoreCase(admin.getEmail())) {
                    admin.setEmail(email);
                }
                adminRepository.save(admin);
                log.warn("Reset the password of admin '{}' because BOOTSTRAP_ADMIN_RESET_PASSWORD is "
                        + "true. Unset that variable now, or every start will reset it again.", email);
                return;
            }

            if (!isBcryptHash(admin.getPasswordHash())) {
                if (password.isEmpty()) {
                    log.error("Admin '{}' has an unusable password hash and BOOTSTRAP_ADMIN_PASSWORD is "
                            + "not set, so it cannot be repaired. Set the password and start again.", email);
                    return;
                }
                admin.setPasswordHash(passwordEncoder.encode(password));
                adminRepository.save(admin);
                log.warn("Admin '{}' had an unusable password hash - reset to the configured bootstrap "
                        + "password. Change it after logging in.", email);
            }
        };
    }

    private boolean isBcryptHash(String hash) {
        return hash != null && BCRYPT_PATTERN.matcher(hash).matches();
    }
}

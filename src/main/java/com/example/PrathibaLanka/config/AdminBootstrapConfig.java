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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

/**
 * Makes sure at least one usable admin account exists.
 *
 * <p>There is no API to create an admin (by design - self-registration only creates customers),
 * so a fresh database has no way to reach any /api/admin/** endpoint at all.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>no admin with the configured email -> the account is created</li>
 *   <li>the account exists but its password hash is not a valid BCrypt hash (e.g. a placeholder
 *       like 'x' inserted by hand) -> the hash is repaired, so the account becomes usable</li>
 *   <li>the account exists with a valid BCrypt hash -> left untouched (passwords are never reset)</li>
 * </ul>
 *
 * Disable in production with app.bootstrap-admin.enabled=false and create admins deliberately.
 */
@Configuration
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    /** BCrypt hashes look like $2a$10$<53 chars>. Anything else can never match a password. */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email}")
    private String adminEmail;

    @Value("${app.bootstrap-admin.password}")
    private String adminPassword;

    @Value("${app.bootstrap-admin.full-name}")
    private String adminFullName;

    @Bean
    @ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner bootstrapAdminRunner() {
        return args -> {
            String email = adminEmail.trim().toLowerCase();

            Admin admin = adminRepository.findByEmailIgnoreCase(email).orElse(null);

            if (admin == null) {
                Admin created = new Admin();
                created.setFullName(adminFullName);
                created.setEmail(email);
                created.setPasswordHash(passwordEncoder.encode(adminPassword));
                created.setRole(Role.ADMIN);
                adminRepository.save(created);

                log.warn("Bootstrapped admin account '{}'. Log in and change this password, or set "
                        + "app.bootstrap-admin.enabled=false and provision admins yourself.", email);
                return;
            }

            if (!isBcryptHash(admin.getPasswordHash())) {
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                adminRepository.save(admin);
                log.warn("Admin '{}' had an unusable password hash - it has been reset to the "
                        + "configured bootstrap password. Change it after logging in.", email);
            }
        };
    }

    private boolean isBcryptHash(String hash) {
        return hash != null && BCRYPT_PATTERN.matcher(hash).matches();
    }
}

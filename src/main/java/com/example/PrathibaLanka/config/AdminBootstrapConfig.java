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
 * Guarantees a usable admin account, because self-registration only creates customers and there is
 * no API to create an admin.
 *
 * <p>Creates the account when missing, and repairs it when the stored hash is not a valid BCrypt
 * hash (e.g. a hand-inserted placeholder). A valid hash is never overwritten.
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

                log.warn("Bootstrapped admin account '{}'. Change this password, or disable "
                        + "app.bootstrap-admin and provision admins yourself.", email);
                return;
            }

            if (!isBcryptHash(admin.getPasswordHash())) {
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                adminRepository.save(admin);
                log.warn("Admin '{}' had an unusable password hash - reset to the configured "
                        + "bootstrap password. Change it after logging in.", email);
            }
        };
    }

    private boolean isBcryptHash(String hash) {
        return hash != null && BCRYPT_PATTERN.matcher(hash).matches();
    }
}

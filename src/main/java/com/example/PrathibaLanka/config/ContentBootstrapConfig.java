package com.example.PrathibaLanka.config;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.enums.ContentSection;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.service.PageContentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Seeds the About and Contact pages from the copy bundled with the application, so the admin
 * console opens on real content instead of empty fields.
 *
 * <p>Only missing rows are created - editing a page in the console is never overwritten by a
 * restart. Disable with {@code app.bootstrap-content.enabled=false}.
 */
@Configuration
@RequiredArgsConstructor
public class ContentBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(ContentBootstrapConfig.class);

    private final PageContentService contentService;
    private final AdminRepository adminRepository;

    @Value("${app.bootstrap-admin.email}")
    private String adminEmail;

    @Bean
    @Order(2)
    @ConditionalOnProperty(name = "app.bootstrap-content.enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner bootstrapContentRunner() {
        return args -> {
            Long adminId = adminRepository.findByEmailIgnoreCase(adminEmail.trim().toLowerCase())
                    .map(Admin::getAdminId)
                    .orElse(null);

            for (ContentSection section : ContentSection.values()) {
                if (contentService.find(section).isPresent()) {
                    continue;
                }
                contentService.seed(section, readDefault(section), adminId);
                log.info("Seeded default {} page content from the bundled defaults.", section);
            }
        };
    }

    private String readDefault(ContentSection section) {
        String path = "content/" + section.name().toLowerCase() + ".json";
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Missing bundled default content: " + path, ex);
        }
    }
}

package com.example.PrathibaLanka.config;

import com.example.PrathibaLanka.security.JwtAuthEntryPoint;
import com.example.PrathibaLanka.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final UserDetailsService userDetailsService;

    /**
     * Origins allowed to call this API from a browser, comma separated.
     *
     * The front end is served from its own origin (a different Railway service, a custom domain, or
     * the dev server), so this has to be configuration rather than a constant: hard-coding localhost
     * is the difference between a working deployment and every request failing its preflight.
     * Set CORS_ALLOWED_ORIGINS in the environment; the local dev origins are the default.
     */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error must stay reachable, otherwise Security's own 403/404 responses
                        // get replaced by a 401 coming from the error dispatch.
                        .requestMatchers("/error").permitAll()
                        // The health endpoint the platform polls. Nothing else under /actuator is
                        // exposed (see management.endpoints.web.exposure.include).
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // A probe or uptime monitor may ask with HEAD; without this, the same URL
                        // that answers 200 to GET answers 401 to HEAD.
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/health/**").permitAll()
                        // Method-scoped so that a write endpoint added under the same path later
                        // is not public by accident.
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/packages", "/api/packages/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews", "/api/reviews/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/gallery", "/api/gallery/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/journal/published", "/api/journal/published/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/content", "/api/content/**").permitAll()
                        // Uploaded images and short videos are part of the public pages.
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        // HEAD is how a link checker or a crawler asks about a file without fetching
                        // it. GET and HEAD are the same read, so they get the same answer.
                        .requestMatchers(HttpMethod.HEAD, "/media/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        // The customer's own enquiry, opened with the token from their acknowledgement
                        // email. No account, no login: the token is the credential, which is the same
                        // trade the booking PIN makes.
                        .requestMatchers(HttpMethod.GET, "/api/enquiries/**").permitAll()
                        // Writing back on it is public too, and rate limited like the other endpoint a
                        // stranger can write to (see app.rate-limit.paths).
                        .requestMatchers(HttpMethod.POST, "/api/enquiries/**").permitAll()
                        // Requesting a journey is the button on a journey card: a visitor with an
                        // email address, not a registered customer, and they need a PIN back. Staff
                        // are kept out by @PreAuthorize on the handler itself.
                        .requestMatchers(HttpMethod.POST, "/api/bookings/request").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/bookings/track").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .headers(headers -> headers
                        // Spring Security's default cache-control writer runs after the handler, so it
                        // silently replaced the 30-day cache header MediaWebConfig sets on uploaded
                        // files: every photograph on every page came back "no-store" and was fetched
                        // again on the next visit. Keep the default everywhere it belongs - API
                        // responses carry customer data - and leave /media/** alone, whose file names
                        // are UUIDs that never change.
                        .cacheControl(cache -> cache.disable())
                        .addHeaderWriter((request, response) -> {
                            if (!request.getRequestURI().startsWith("/media/")) {
                                response.setHeader("Cache-Control",
                                        "no-cache, no-store, max-age=0, must-revalidate");
                                response.setHeader("Pragma", "no-cache");
                                response.setHeader("Expires", "0");
                            }
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (origins.isEmpty()) {
            // An empty list would make Spring reject every cross-origin call while looking like it
            // is configured, which is a confusing way to fail.
            throw new IllegalStateException(
                    "app.cors.allowed-origins is empty - set CORS_ALLOWED_ORIGINS to the front end's origin.");
        }
        log.info("CORS: allowing browser calls from {}", origins);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        // Cache the preflight: without it every POST pays for an extra round trip.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.LoginRequestDTO;
import com.example.PrathibaLanka.dto.request.RegisterRequestDTO;
import com.example.PrathibaLanka.dto.response.AuthResponseDTO;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import com.example.PrathibaLanka.security.JwtService;
import com.example.PrathibaLanka.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CustomerRepository customerRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Login for both admins and customers. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(dto.getEmail()), dto.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String token = jwtService.generateToken(principal);
        String role = principal.getAuthorities().iterator().next().getAuthority();

        return ResponseEntity.ok(new AuthResponseDTO(
                token, principal.getEmail(), role, principal.getUserId()
        ));
    }

    /** Public customer registration. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO dto) {
        String email = normalizeEmail(dto.getEmail());

        // Enforce globally unique email across Admin and Customer (case-insensitive)
        if (adminRepository.findByEmailIgnoreCase(email).isPresent() ||
                customerRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BadRequestException("Email is already registered.");
        }

        Customer customer = new Customer();
        customer.setFullName(dto.getFullName().trim());
        customer.setEmail(email);
        customer.setPhone(dto.getPhone());
        customer.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        Customer saved = customerRepository.save(customer);

        UserPrincipal principal = UserPrincipal.fromCustomer(saved);
        String token = jwtService.generateToken(principal);

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponseDTO(
                token, principal.getEmail(), "ROLE_CUSTOMER", principal.getUserId()
        ));
    }

    /**
     * Emails are stored and compared in a single canonical form, otherwise "User@x.com" and
     * "user@x.com" can both register (duplicate accounts) and only the exact casing can log in.
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}